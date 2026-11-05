package io.github.xieyinch.agentscope;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;

/** Semantic colors and original line icons, inspired by HIG clarity and hierarchy. */
final class Ui {
    final int bg,surface,secondary,ink,muted,line,blue,red,green;
    final boolean dark;
    Ui(Context c){
        dark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        bg=dark?0xff000000:0xfff2f2f7;surface=dark?0xff1c1c1e:0xffffffff;secondary=dark?0xff2c2c2e:0xfff2f2f7;
        ink=dark?0xfff2f2f7:0xff1c1c1e;muted=dark?0xffaeaeb2:0xff6c6c70;line=dark?0xff38383a:0xffe5e5ea;
        blue=dark?0xff0a84ff:0xff007aff;red=dark?0xffff453a:0xffff3b30;green=dark?0xff30d158:0xff248a3d;
    }
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(c,radius));return s;}
    static RippleDrawable ripple(Context c,int color,int radius,int tint){return new RippleDrawable(ColorStateList.valueOf((tint&0xffffff)|0x18000000),shape(c,color,radius),shape(c,Color.WHITE,radius));}
    static final class Icon extends View {
        private String kind;private int color;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c,String kind,int color){super(c);this.kind=kind;this.color=color;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void tint(int color){this.color=color;invalidate();}
        @Override protected void onDraw(Canvas source){super.onDraw(source);Canvas c=source;c.save();float size=Math.min(getWidth(),getHeight()),scale=size/24f;c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(scale,scale);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            Path path=new Path();
            switch(kind){
                case "chat":c.drawRoundRect(3,4,21,18,5,5,p);path.moveTo(7,18);path.lineTo(6,22);path.lineTo(12,18);c.drawPath(path,p);break;
                case "settings":for(int y:new int[]{6,12,18})c.drawLine(3,y,21,y,p);for(int[] a:new int[][]{{8,6},{16,12},{10,18}}){p.setStyle(Paint.Style.FILL);p.setColor(getBackground()==null?Color.TRANSPARENT:color);p.setStyle(Paint.Style.STROKE);c.drawCircle(a[0],a[1],2.5f,p);}break;
                case "community":c.drawCircle(9,8,3.5f,p);c.drawArc(2,14,16,26,180,180,false,p);c.drawArc(12,4,21,12,260,190,false,p);c.drawArc(14,14,23,25,240,110,false,p);break;
                case "person":c.drawCircle(12,7,4,p);c.drawRoundRect(4,14,20,23,7,7,p);break;
                case "refresh":c.drawArc(4,4,20,20,40,290,false,p);path.moveTo(20,4);path.lineTo(20,10);path.lineTo(14,10);c.drawPath(path,p);break;
                case "back":path.moveTo(15,5);path.lineTo(8,12);path.lineTo(15,19);c.drawPath(path,p);break;
                case "chevron":path.moveTo(9,6);path.lineTo(15,12);path.lineTo(9,18);c.drawPath(path,p);break;
                case "cloud":path.moveTo(6,18);path.cubicTo(-1,17,1,8,7,9);path.cubicTo(8,1,19,2,19,10);path.cubicTo(25,10,24,18,18,18);path.close();c.drawPath(path,p);break;
                case "terminal":c.drawRoundRect(2,4,22,20,4,4,p);path.moveTo(6,9);path.lineTo(9,12);path.lineTo(6,15);c.drawPath(path,p);c.drawLine(12,15,17,15,p);break;
                case "folder":path.moveTo(2,8);path.lineTo(2,5);path.lineTo(9,5);path.lineTo(11,8);path.lineTo(22,8);path.lineTo(22,20);path.lineTo(2,20);path.close();c.drawPath(path,p);break;
                case "shield":path.moveTo(12,2);path.lineTo(21,6);path.lineTo(20,14);path.quadTo(18,20,12,23);path.quadTo(5,20,4,14);path.lineTo(3,6);path.close();c.drawPath(path,p);break;
                case "clock":c.drawCircle(12,12,9,p);c.drawLine(12,6,12,12,p);c.drawLine(12,12,16,14,p);break;
                default:for(int x:new int[]{3,14})for(int y:new int[]{3,14})c.drawRoundRect(x,y,x+7,y+7,2,2,p);break;
            }c.restore();
        }
    }
}
