package com.pureqml.android;

import java.util.LinkedList;
import java.util.List;

public final class FontFamily {
    public final String family;
    public final int weight;
    public final boolean italic;
    public final boolean oblique;
    FontFamily(String family, int weight, boolean italic, boolean oblique) {
        this.family = family;
        this.weight = weight;
        this.italic = italic;
        this.oblique = oblique;
    }
    public String toString() {
        return String.format("FontFamily { %s, weight: %d, italic: %s, oblique: %s}", family, weight, italic, oblique);
    }
    static FontFamily parse(String name) {
        int weight = 400; //Regular
        boolean italic = false;
        boolean oblique = false;
        List<String> filtered = new LinkedList<String>();
        for(String token : name.split("\\s+")) {
            switch (token) {
                case "Italic":
                    italic = true;
                    break;
                case "Oblique":
                    oblique = true;
                    break;
                case "Thin":
                case "Hairline":
                    weight = 100;
                    break;
                case "ExtraLight":
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
                case "Black":
                case "Heavy":
                    weight = 900;
                    break;
                case "ExtraBlack":
                case "UltraBlack":
                    weight = 950;
                    break;
                default:
                    filtered.add(token);
                    break;
            }
        }
        String family = String.join(" ", filtered);
        return new FontFamily(family, weight, italic, oblique);
    }
}
