package com.kiri.server.domain.external.client.kma;

// 기상청 동네예보 격자 좌표
public record KmaGrid(int nx, int ny) {

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_KM = 5.0;
    private static final double STANDARD_LAT1 = Math.toRadians(30.0);
    private static final double STANDARD_LAT2 = Math.toRadians(60.0);
    private static final double ORIGIN_LON = Math.toRadians(126.0);
    private static final double ORIGIN_LAT = Math.toRadians(38.0);
    private static final double ORIGIN_X = 43;
    private static final double ORIGIN_Y = 136;

    private static final double RE = EARTH_RADIUS_KM / GRID_KM;
    private static final double SN = Math.log(Math.cos(STANDARD_LAT1) / Math.cos(STANDARD_LAT2))
            / Math.log(tanHalf(STANDARD_LAT2) / tanHalf(STANDARD_LAT1));
    private static final double SF = Math.pow(tanHalf(STANDARD_LAT1), SN) * Math.cos(STANDARD_LAT1) / SN;
    private static final double RO = RE * SF / Math.pow(tanHalf(ORIGIN_LAT), SN);

    // 기상청 공개 변환식
    public static KmaGrid from(double latitude, double longitude) {
        double ra = RE * SF / Math.pow(tanHalf(Math.toRadians(latitude)), SN);
        double theta = Math.toRadians(longitude) - ORIGIN_LON;
        if (theta > Math.PI) {
            theta -= 2 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2 * Math.PI;
        }
        theta *= SN;
        int nx = (int) Math.floor(ra * Math.sin(theta) + ORIGIN_X + 0.5);
        int ny = (int) Math.floor(RO - ra * Math.cos(theta) + ORIGIN_Y + 0.5);
        return new KmaGrid(nx, ny);
    }

    private static double tanHalf(double radian) {
        return Math.tan(Math.PI * 0.25 + radian * 0.5);
    }
}
