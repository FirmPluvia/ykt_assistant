package com.ykt.assistant;

import java.util.List;
import java.util.Locale;

/** Camera-driver zoom indices and ratios; pinch accumulates even between coarse hardware steps. */
final class CameraZoom {
    private int[] ratios={100}; private int index; private double requested=100;
    void configure(boolean supported,List<Integer> values,int maximum,int current) {
        ratios=new int[]{100}; index=0; requested=100;
        if(!supported||values==null||maximum<1||maximum>=values.size()||maximum>4096) return;
        int[] next=new int[maximum+1];
        for(int i=0;i<next.length;i++) { Integer value=values.get(i); if(value==null||value<100||(i>0&&value<next[i-1])) return; next[i]=value; }
        if(next[next.length-1]<=next[0]) return;
        ratios=next; setIndex(current);
    }
    boolean supported() { return ratios.length>1; }
    int maximum() { return ratios.length-1; }
    int index() { return index; }
    double factor() { return ratios[index]/100.0; }
    String label() { return String.format(Locale.ROOT,"%.1f×",factor()); }
    void setIndex(int value) { index=Math.max(0,Math.min(maximum(),value)); requested=ratios[index]; }
    void setFactor(double factor) { if(!Double.isFinite(factor)||factor<=0) return; requested=bound(factor*100); index=nearest(requested); }
    void beginGesture() { requested=ratios[index]; }
    void scale(double factor) { if(!supported()||!Double.isFinite(factor)||factor<=0) return; requested=bound(requested*factor); index=nearest(requested); }
    void nudge(int direction) { if(!supported()||direction==0) return; int previous=index; setFactor(factor()+Math.signum(direction)*0.25); if(index==previous) setIndex(index+(direction>0?1:-1)); }
    void reset() { setIndex(0); }
    private double bound(double value) { return Math.max(ratios[0],Math.min(ratios[maximum()],value)); }
    private int nearest(double value) { int best=0; for(int i=1;i<ratios.length;i++) if(Math.abs(ratios[i]-value)<Math.abs(ratios[best]-value)) best=i; return best; }
}
