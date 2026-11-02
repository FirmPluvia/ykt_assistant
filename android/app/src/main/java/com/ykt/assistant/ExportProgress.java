package com.ykt.assistant;

/** Real progress in each phase; the build phase is indeterminate, not a fabricated total percentage. */
final class ExportProgress {
    enum Stage { FETCH,BUILD,SAVE }
    final Stage stage;
    final int pages,totalPages;
    final long bytes,totalBytes;
    private ExportProgress(Stage stage,int pages,int totalPages,long bytes,long totalBytes) {
        this.stage=stage; this.totalPages=Math.max(0,totalPages); this.pages=Math.max(0,Math.min(pages,this.totalPages)); this.totalBytes=Math.max(0,totalBytes); this.bytes=Math.max(0,Math.min(bytes,this.totalBytes));
    }
    static ExportProgress fetching(int pages,int total) { return new ExportProgress(Stage.FETCH,pages,total,0,0); }
    static ExportProgress building(int total) { return new ExportProgress(Stage.BUILD,total,total,0,0); }
    static ExportProgress saving(int total,long bytes,long length) { return new ExportProgress(Stage.SAVE,total,total,bytes,length); }
    int percent() {
        if(stage==Stage.FETCH) return totalPages==0?0:(int)(pages*100L/totalPages);
        if(stage==Stage.SAVE) return totalBytes==0?0:(int)(bytes*100L/totalBytes);
        return 0;
    }
}
