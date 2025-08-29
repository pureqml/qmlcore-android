package com.pureqml.android;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public final class Font implements Comparable<Font> {
    public final String family;
    public final int weight;
    public final boolean italic;
    Font(String family, int weight, boolean italic) {
        this.family = family;
        this.weight = weight;
        this.italic = italic;
    }

    @NonNull
    public String toString() {
        return String.format(Locale.getDefault(), "Font { %s, weight: %d, italic: %s}", family, weight, italic);
    }
    public static Font parse(String name) {
        int weight = 400; //Regular
        boolean italic = false;
        List<String> filtered = new LinkedList<>();
        for(String token : name.split("\\s+")) {
            switch (token) {
                case "Italic":
                    italic = true;
                    break;
                case "Oblique":
                    break;
                case "Hairline":
                    weight = 50;
                    break;
                case "Thin":
                    weight = 100;
                    break;
                case "ExtraLight":
                    weight = 150;
                    break;
                case "UltraLight":
                    weight = 200;
                    break;
                case "Light":
                    weight = 300;
                    break;
                case "Normal":
                case "Regular":
                    weight = 400;
                    break;
                case "Medium":
                    weight = 500;
                    break;
                case "SemiBold":
                case "DemiBold":
                    weight = 600;
                    break;
                case "Bold":
                    weight = 700;
                    break;
                case "ExtraBold":
                case "UltraBold":
                    weight = 800;
                    break;
                case "Heavy":
                    weight = 900;
                    break;
                case "Black":
                    weight = 925;
                    break;
                case "UltraBlack":
                    weight = 950;
                    break;
                case "ExtraBlack":
                    weight = 975;
                    break;
                default:
                    filtered.add(token);
                    break;
            }
        }
        String family = String.join(" ", filtered);
        return new Font(family, weight, italic);
    }

    @Override
    public int compareTo(Font o) {
        int r = family.compareTo(o.family);
        if (r != 0)
            return r;
        r = Boolean.compare(italic, o.italic);
        if (r != 0)
            return r;

        return weight - o.weight;
    }
}
