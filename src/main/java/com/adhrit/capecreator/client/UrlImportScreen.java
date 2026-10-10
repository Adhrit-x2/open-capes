package com.adhrit.capecreator.client;

import com.adhrit.capecreator.CapeCreatorClient;
import com.adhrit.capecreator.CapeLibrary;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Paste a link to a GIF or image; it's downloaded in the background and opened in the crop screen. */
public class UrlImportScreen extends Screen {
    private static final int MAX_BYTES = 40 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Screen parent;
    private final Consumer<CropScreen.Result> onDone;
    private final String initialUrl;
    private TextFieldWidget urlField;
    private ButtonWidget loadButton;
    private Text status = Text.empty();
    private boolean loading;

    public UrlImportScreen(Screen parent, String initialUrl, Consumer<CropScreen.Result> onDone) {
        super(Text.literal("Import from a link"));
        this.parent = parent;
        this.initialUrl = initialUrl == null ? "" : initialUrl;
        this.onDone = onDone;
    }

    /** True if the text looks like something worth trying to download. */
    public static boolean looksLikeUrl(String text) {
        String t = text == null ? "" : text.trim().toLowerCase();
        return t.startsWith("http://") || t.startsWith("https://");
    }

    @Override
    protected void init() {
        int w = Math.min(360, width - 40);
        urlField = new TextFieldWidget(textRenderer, (width - w) / 2, height / 2 - 30, w, 20, Text.literal("Link"));
        urlField.setMaxLength(2048);
        urlField.setPlaceholder(Text.literal("Paste a GIF or image link (Ctrl/Cmd+V)"));
        if (urlField.getText().isEmpty()) urlField.setText(initialUrl);
        addDrawableChild(urlField);
        setInitialFocus(urlField);

        loadButton = addDrawableChild(ButtonWidget.builder(Text.literal("Load"), b -> load())
                .dimensions(width / 2 - 102, height / 2 + 4, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(width / 2 + 2, height / 2 + 4, 100, 20).build());

        if (looksLikeUrl(initialUrl) && !loading && status.getString().isEmpty()) load();
    }

    private void load() {
        String url = urlField.getText().trim();
        if (!looksLikeUrl(url)) {
            status = Text.literal("That doesn't look like a link (it should start with https://)");
            return;
        }
        loading = true;
        loadButton.active = false;
        status = Text.literal("Downloading...");
        CompletableFuture.supplyAsync(() -> {
            try {
                return SourceImage.decode(download(url));
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, Util.getDownloadWorkerExecutor()).whenComplete((source, error) -> client.execute(() -> {
            loading = false;
            if (loadButton != null) loadButton.active = true;
            if (error != null) {
                CapeCreatorClient.LOGGER.warn("Could not import cape from {}", url, error);
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                status = Text.literal(cause.getMessage() == null ? "Download failed" : cause.getMessage());
            } else if (client.currentScreen == this) {
                client.setScreen(new CropScreen(parent, source, result -> {
                    onDone.accept(result);
                    saveToLibrary(url);
                }));
            }
        }));
    }

    /** Every cape imported from a link is also added to the saved capes. */
    private void saveToLibrary(String url) {
        try {
            CapeLibrary.Entry entry = CapeLibrary.addCurrent(CapeLibrary.uniqueName(CapeLibrary.nameFromUrl(url)));
            SystemToast.show(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Cape saved"), Text.literal("Added \"" + entry.name() + "\" to your saved capes"));
        } catch (Exception e) {
            CapeCreatorClient.LOGGER.warn("Could not save imported cape", e);
            SystemToast.show(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Couldn't save cape"), Text.literal(String.valueOf(e.getMessage())));
        }
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        // Look like a browser that found the image on the site's own page; many image hosts
        // refuse "hotlinked" downloads (HTTP 403) without a matching Referer.
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .header("Accept", "image/gif,image/png,image/jpeg,image/*;q=0.9,*/*;q=0.5")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", uri.getScheme() + "://" + uri.getHost() + "/")
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() == 403 || response.statusCode() == 401) {
                throw new IOException("That site blocked the download (HTTP " + response.statusCode()
                        + "). Try saving the GIF and dropping the file onto this window instead.");
            }
            if (response.statusCode() / 100 != 2) throw new IOException("The site said HTTP " + response.statusCode());
            String type = response.headers().firstValue("Content-Type").orElse("");
            if (type.startsWith("text/html")) {
                throw new IOException("That's a web page, not an image. Right-click the GIF and choose \"Copy Image Address\".");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > MAX_BYTES) throw new IOException("That file is too big (over 40 MB)");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 52, 0xFFFFFFFF);
        int y = height / 2 + 34;
        for (var line : textRenderer.wrapLines(status, Math.min(360, width - 40))) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, y, loading ? 0xFFA0A0A0 : 0xFFFF8080);
            y += 10;
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
