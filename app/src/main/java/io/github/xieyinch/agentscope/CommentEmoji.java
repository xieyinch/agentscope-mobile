package io.github.xieyinch.agentscope;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSource;

/** Renders the community's inline emoji tokens with their original CDN images. */
final class CommentEmoji {
    private static final Pattern TOKEN = Pattern.compile("\\[emoji:([^\\]\\r\\n]*)\\]");
    private static final String PLACEHOLDER = "[表情]";
    private static final long MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DIMENSION = 1024;
    // The media client has no access to the account's cookies or authorization headers.
    private final OkHttpClient client;

    CommentEmoji() {
        this(new OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(12, TimeUnit.SECONDS)
            .build());
    }

    CommentEmoji(OkHttpClient client) {
        this.client = client;
    }

    void bind(TextView view, String content, int size, Runnable onLayoutNeeded) {
        Matcher tokens = TOKEN.matcher(content);
        if (!tokens.find()) {
            view.setText(content);
            return;
        }

        SpannableStringBuilder text = new SpannableStringBuilder();
        List<Emoji> emojis = new ArrayList<>();
        int end = 0;
        do {
            text.append(content, end, tokens.start());
            int start = text.length();
            text.append(PLACEHOLDER);
            HttpUrl url = HttpUrl.parse(tokens.group(1));
            if (url != null && url.isHttps() && url.host().equals("img.alicdn.com")
                    && url.port() == 443 && url.username().isEmpty() && url.password().isEmpty()
                    && url.encodedPath().startsWith("/imgextra/")) {
                emojis.add(new Emoji(start, text.length(), url));
            }
            end = tokens.end();
        } while (tokens.find());
        text.append(content, end, content.length());
        view.setText(text, TextView.BufferType.SPANNABLE);

        View.OnAttachStateChangeListener binding = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                for (Emoji emoji : emojis) {
                    if (emoji.image == null) load(view, emoji, size, onLayoutNeeded);
                    else animate(view, emoji.image);
                }
            }

            @Override public void onViewDetachedFromWindow(View v) {
                for (Emoji emoji : emojis) {
                    if (emoji.call != null) emoji.call.cancel();
                    if (Build.VERSION.SDK_INT >= 28 && emoji.image instanceof AnimatedImageDrawable) {
                        ((AnimatedImageDrawable) emoji.image).stop();
                        emoji.image.setCallback(null);
                    }
                }
            }
        };
        if (!emojis.isEmpty()) {
            view.addOnAttachStateChangeListener(binding);
            if (view.isAttachedToWindow()) binding.onViewAttachedToWindow(view);
        }
    }

    private void load(TextView view, Emoji emoji, int size, Runnable onLayoutNeeded) {
        Call call = client.newCall(new Request.Builder().url(emoji.url).build());
        emoji.call = call;
        Resources resources = view.getResources();
        call.enqueue(new Callback() {
            @Override public void onFailure(Call ignored, IOException error) {
                // The readable placeholder remains in the comment.
            }

            @Override public void onResponse(Call request, Response response) {
                try (response) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    BufferedSource source = response.body().source();
                    if (source.request(MAX_BYTES + 1)) return;
                    Drawable image = decode(resources, source.readByteArray(), size);
                    if (image == null) return;
                    view.post(() -> {
                        if (call.isCanceled() || !view.isAttachedToWindow()
                                || !(view.getText() instanceof Spannable)) return;
                        emoji.image = image;
                        Spannable displayed = (Spannable) view.getText();
                        displayed.setSpan(new ImageSpan(image, ImageSpan.ALIGN_BASELINE),
                            emoji.start, emoji.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        animate(view, image);
                        view.post(onLayoutNeeded);
                    });
                } catch (IOException | RuntimeException ignored) {
                    // An unavailable or malformed image leaves the placeholder in place.
                }
            }
        });
    }

    private static Drawable decode(Resources resources, byte[] bytes, int size) throws IOException {
        Drawable image;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source source = ImageDecoder.createSource(ByteBuffer.wrap(bytes));
            image = ImageDecoder.decodeDrawable(source, (decoder, info, ignored) -> {
                int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                if (Math.max(width, height) > MAX_DIMENSION) throw new IllegalArgumentException();
                float ratio = Math.min(1f, (float) size / Math.max(width, height));
                decoder.setTargetSize(Math.max(1, Math.round(width * ratio)),
                    Math.max(1, Math.round(height * ratio)));
            });
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            int longest = Math.max(options.outWidth, options.outHeight);
            if (longest <= 0 || longest > MAX_DIMENSION) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (longest / options.inSampleSize > size * 2) options.inSampleSize *= 2;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) return null;
            image = new BitmapDrawable(resources, bitmap);
        }
        float ratio = (float) size / Math.max(image.getIntrinsicWidth(), image.getIntrinsicHeight());
        image.setBounds(0, 0, Math.max(1, Math.round(image.getIntrinsicWidth() * ratio)),
            Math.max(1, Math.round(image.getIntrinsicHeight() * ratio)));
        return image;
    }

    private static void animate(TextView view, Drawable image) {
        if (Build.VERSION.SDK_INT < 28 || !(image instanceof AnimatedImageDrawable)) return;
        AnimatedImageDrawable animation = (AnimatedImageDrawable) image;
        animation.setCallback(new Drawable.Callback() {
            @Override public void invalidateDrawable(Drawable drawable) { view.invalidate(); }

            @Override public void scheduleDrawable(Drawable drawable, Runnable action, long when) {
                view.postDelayed(action, Math.max(0, when - SystemClock.uptimeMillis()));
            }

            @Override public void unscheduleDrawable(Drawable drawable, Runnable action) {
                view.removeCallbacks(action);
            }
        });
        animation.start();
    }

    private static final class Emoji {
        final int start;
        final int end;
        final HttpUrl url;
        Call call;
        Drawable image;

        Emoji(int start, int end, HttpUrl url) {
            this.start = start;
            this.end = end;
            this.url = url;
        }
    }
}
