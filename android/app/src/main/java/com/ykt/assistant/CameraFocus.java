package com.ykt.assistant;

import android.graphics.Rect;
import java.util.List;

/** Maps the visible, rotated/cropped preview back to legacy camera sensor coordinates. */
final class CameraFocus {
    static Rect area(float x,float y,Rect preview,int rotation,boolean mirrored) {
        if(preview==null||preview.width()<=0||preview.height()<=0||!Float.isFinite(x)||!Float.isFinite(y)) return null;
        if(!preview.contains((int)x,(int)y)) return null;
        double u=(x-preview.left)/preview.width(),v=(y-preview.top)/preview.height();
        double a=u,b=v;
        switch((rotation%360+360)%360) {
            case 90: a=v; b=1-u; break;
            case 180: a=1-u; b=1-v; break;
            case 270: a=1-v; b=u; break;
            default: break;
        }
        // Camera mirrors sensor coordinates before display rotation; undo in reverse order.
        if(mirrored) a=1-a;
        int cx=(int)Math.round(a*2000-1000),cy=(int)Math.round(b*2000-1000);
        int left=Math.max(-1000,Math.min(760,cx-120)),top=Math.max(-1000,Math.min(760,cy-120));
        return new Rect(left,top,left+240,top+240);
    }
    static String automaticMode(List<String> modes) {
        if(modes==null) return null;
        for(String mode:new String[]{"continuous-picture","continuous-video","auto"}) if(modes.contains(mode)) return mode;
        return null;
    }
}
