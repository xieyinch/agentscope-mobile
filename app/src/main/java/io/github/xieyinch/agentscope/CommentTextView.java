package io.github.xieyinch.agentscope;

import android.content.Context;
import android.text.Layout;
import android.view.Gravity;
import android.widget.TextView;

/** Keeps the full text layout so folding cannot hide the evidence of overflow. */
final class CommentTextView extends TextView {
    private static final int PREVIEW_LINES = 4;
    private boolean expanded;

    CommentTextView(Context context) {
        super(context);
        setGravity(Gravity.TOP | Gravity.START);
    }

    boolean hasHiddenLines() {
        Layout layout = getLayout();
        return layout != null && layout.getLineCount() > PREVIEW_LINES;
    }

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean expanded) {
        this.expanded = expanded;
        if (!expanded) scrollTo(0, 0);
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (!expanded && hasHiddenLines()) {
            int previewHeight = getCompoundPaddingTop()
                + getLayout().getLineBottom(PREVIEW_LINES - 1)
                + getCompoundPaddingBottom();
            setMeasuredDimension(getMeasuredWidthAndState(),
                resolveSizeAndState(previewHeight, heightSpec, 0));
        }
    }

    @Override public void scrollTo(int x, int y) {
        // Selection must not scroll the folded viewport away from the first line.
        super.scrollTo(expanded ? x : 0, expanded ? y : 0);
    }
}
