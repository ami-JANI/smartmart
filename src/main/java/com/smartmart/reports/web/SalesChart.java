package com.smartmart.reports.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.smartmart.reports.service.Dashboard.DaySales;

/**
 * The 30-day sales chart as SVG coordinates, so the page draws it without a
 * chart library. The drawing area is {@link #WIDTH} × {@link #HEIGHT} units.
 *
 * @param line   the polyline points, "x,y x,y ..."
 * @param area   the closed path under the line, for the filled area
 * @param max    the highest day's sales, the top of the chart
 * @param labels a date label every 5 days: x and text
 * @param points every day's point, for the dots and their accessible titles
 */
record SalesChart(String line, String area, BigDecimal max, List<Label> labels, List<Point> points) {

    static final int WIDTH = 600;
    static final int HEIGHT = 200;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    record Label(double x, String text) {
    }

    record Point(double x, double y, String title) {
    }

    static SalesChart of(List<DaySales> days) {
        BigDecimal max = days.stream().map(DaySales::amount).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        double top = max.signum() == 0 ? 1 : max.doubleValue();
        double step = days.size() > 1 ? (double) WIDTH / (days.size() - 1) : 0;
        List<Point> points = new ArrayList<>();
        List<Label> labels = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < days.size(); i++) {
            DaySales day = days.get(i);
            double x = round(i * step);
            // 10 units of space at the top so the highest point is not cut off.
            double y = round(HEIGHT - day.amount().doubleValue() / top * (HEIGHT - 10));
            points.add(new Point(x, y, LABEL.format(day.day()) + ": ৳ " + day.amount().setScale(2, RoundingMode.HALF_UP)));
            line.append(i == 0 ? "" : " ").append(x).append(',').append(y);
            if (i % 5 == 0 || i == days.size() - 1) {
                labels.add(new Label(x, LABEL.format(day.day())));
            }
        }
        String area = days.isEmpty() ? "" : "M0," + HEIGHT + " L" + line.toString().replace(" ", " L")
                + " L" + WIDTH + "," + HEIGHT + " Z";
        return new SalesChart(line.toString(), area, max, labels, points);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
