package com.ykt.assistant;

/** Coordinates selected for one submission; the server's target remains immutable. */
final class LocationCoordinates {
    static final double EARTH_METRES=6371008.8,RANDOM_RADIUS_METRES=50;
    static LocationCheckin.Point parse(String latitude,String longitude) throws IllegalArgumentException {
        double lat=number(latitude,"纬度"),lng=number(longitude,"经度");
        return validate(new LocationCheckin.Point(lat,lng));
    }
    private static double number(String value,String label) {
        try { if(value==null||value.trim().isEmpty()) throw new NumberFormatException(); double v=Double.parseDouble(value.trim()); if(!Double.isFinite(v)) throw new NumberFormatException(); return v; }
        catch(NumberFormatException e) { throw new IllegalArgumentException("请输入有效的"+label); }
    }
    static LocationCheckin.Point validate(LocationCheckin.Point point) {
        if(point==null||!Double.isFinite(point.lat)||Math.abs(point.lat)>90) throw new IllegalArgumentException("纬度须在 -90 到 90 之间");
        if(!Double.isFinite(point.lng)||Math.abs(point.lng)>180) throw new IllegalArgumentException("经度须在 -180 到 180 之间"); return point;
    }
    static String format(double value) { return String.format(java.util.Locale.ROOT,"%.6f",value); }
    static final String RADIUS_KEY="location_random_radius_metres";
    static double parseRadius(String value) { double metres=number(value,"随机距离"); if(metres<1||metres>10000) throw new IllegalArgumentException("随机距离须在 1 到 10000 米之间"); return metres; }
    static String radiusLabel(double metres) { return java.math.BigDecimal.valueOf(metres).stripTrailingZeros().toPlainString(); }
    static LocationCheckin.Point random(LocationCheckin.Point center) { return random(center,RANDOM_RADIUS_METRES); }
    static LocationCheckin.Point random(LocationCheckin.Point center,double radius) { return random(center,radius,Math.random(),Math.random()); }
    static LocationCheckin.Point random(LocationCheckin.Point center,double areaFraction,double bearingFraction) {
        return random(center,RANDOM_RADIUS_METRES,areaFraction,bearingFraction);
    }
    static LocationCheckin.Point random(LocationCheckin.Point center,double radius,double areaFraction,double bearingFraction) {
        validate(center); parseRadius(Double.toString(radius));
        if(!Double.isFinite(areaFraction)||!Double.isFinite(bearingFraction)||areaFraction<0||areaFraction>1||bearingFraction<0||bearingFraction>1) throw new IllegalArgumentException("随机参数无效");
        double distance=(radius-0.1)*Math.sqrt(areaFraction)/EARTH_METRES,bearing=2*Math.PI*bearingFraction;
        double lat=Math.toRadians(center.lat),lng=Math.toRadians(center.lng);
        double targetLat=Math.asin(Math.max(-1,Math.min(1,Math.sin(lat)*Math.cos(distance)+Math.cos(lat)*Math.sin(distance)*Math.cos(bearing))));
        double targetLng=lng+Math.atan2(Math.sin(bearing)*Math.sin(distance)*Math.cos(lat),Math.cos(distance)-Math.sin(lat)*Math.sin(targetLat));
        double longitude=(Math.toDegrees(targetLng)+540)%360-180;
        return validate(new LocationCheckin.Point(Math.toDegrees(targetLat),longitude));
    }
}
