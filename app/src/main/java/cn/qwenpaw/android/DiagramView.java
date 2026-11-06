package cn.qwenpaw.android;
import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;
/** Native selectable diagram source; does not run HTML or JavaScript. */
public final class DiagramView extends androidx.appcompat.widget.AppCompatTextView {
    public DiagramView(Context context,String source){super(context);setText(source);setTypeface(Typeface.MONOSPACE);setTextSize(13);setTextColor(Ui.INK);setBackgroundColor(Ui.SOFT);setPadding(20,16,20,16);setTextIsSelectable(true);}
    public void destroy(){}
}
