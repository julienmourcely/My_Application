package com.example.myapplication.ui.home;

public class Point {
    double lat, lon, alt;
    static double R = 6378137; // ra dius earth in meter

    Point(double x, double y) {
        lat=x; lon=y; alt=1.5;
    }
    // Distance between to GPS coordinates
    double distance(Point b) {
        // d=acos(sin (lat1)*sin(lat2)+ cos (lat1)*cos(lat2)*cos(lon1-lon2))
        //System.out.println(this.lat+" "+b.lat+" "+this.lon+" "+b.lon);
        double dLon = (b.lon-this.lon)/180*Math.PI;
        double l1=this.lat/180*Math.PI;
        double l2=b.lat/180*Math.PI;
        double alpha = Math.sin(l1) * Math.sin(l2) + Math.cos(l1) * Math.cos(l2)
                * Math.cos(dLon);
        double c = Math.acos(alpha);
        return R * c;
    }
    // Azimut/direction of point b from point a (attached object)
    double azimut(Point b) {
        double dLon = (b.lon-this.lon)/180*Math.PI;
        double l1=this.lat/180*Math.PI;
        double l2=b.lat/180*Math.PI;
        double x = Math.sin(dLon) * Math.cos(l2);
        double y = Math.cos(l1) * Math.sin(l2) - Math.sin(l1) * Math.cos(l2) *
                Math.cos(dLon);
        double aziRad = Math.atan2(y,x);
        return 180 * aziRad / Math.PI;
    }

    // Calculus of the destination coordinates using origin coordinates, distance, and azimuth of the destination point
    Point destination(double d, double azi) {
        Point dest = new Point(0, 0);
        double l1 = this.lat / 180 * Math.PI; // Convert latitude from degrees to radians
        double aziRad = azi / 180 * Math.PI; // Convert azimuth from degrees to radians

        // Calculate destination latitude
        double destLat = Math.asin(Math.sin(l1) * Math.cos(d / R) +
                Math.cos(l1) * Math.sin(d / R) * Math.cos(aziRad));

        // Calculate intermediate values for destination longitude
        double y = Math.sin(aziRad) * Math.sin(d / R) * Math.cos(l1);
        double x = Math.cos(d / R) - Math.sin(l1) * Math.sin(destLat);

        // Set destination coordinates
        dest.lat = 180 * destLat / Math.PI; // Convert destination latitude back to degrees
        dest.lon = this.lon + 180 * Math.atan2(y, x) / Math.PI; // Adjust longitude and convert to degrees

        return dest;
    }

}
