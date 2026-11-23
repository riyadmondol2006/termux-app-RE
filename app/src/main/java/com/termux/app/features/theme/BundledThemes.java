package com.termux.app.features.theme;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Original color schemes designed for Termux RE (MIT, see assets/theme/NOTICE.md).
 * Values are well-known harmonious palettes authored for this project; no third-party scheme files are bundled.
 */
public final class BundledThemes {

    private BundledThemes() {}

    private static int c(String hex) { return TermuxTheme.parseHex(hex); }

    private static TermuxTheme t(String id, String name, String fg, String bg, String cur, String... ansi) {
        int[] a = new int[TermuxTheme.ANSI_COUNT];
        for (int i = 0; i < a.length; i++) a[i] = c(ansi[i]);
        return new TermuxTheme(id, name, c(fg), c(bg), c(cur), a);
    }

    @NonNull public static List<TermuxTheme> all() {
        List<TermuxTheme> list = new ArrayList<>();
        list.add(t("re-midnight", "RE Midnight", "#E6E6E6", "#0B0E14", "#FFCC66",
            "#0B0E14","#E06C75","#98C379","#E5C07B","#61AFEF","#C678DD","#56B6C2","#ABB2BF",
            "#545862","#E06C75","#98C379","#E5C07B","#61AFEF","#C678DD","#56B6C2","#FFFFFF"));
        list.add(t("re-paper", "RE Paper (light)", "#2B2B2B", "#FAF7F0", "#D33682",
            "#EEE8D5","#DC322F","#859900","#B58900","#268BD2","#D33682","#2AA198","#657B83",
            "#93A1A1","#CB4B16","#859900","#B58900","#268BD2","#D33682","#2AA198","#002B36"));
        list.add(t("re-forest", "RE Forest", "#D8E8D0", "#0E1A12", "#A8E063",
            "#0E1A12","#E06C5B","#7FB069","#E8C547","#5B8E7D","#BC6C9B","#6FC3A7","#CAD2C5",
            "#3A4A3A","#FF8A75","#A8E063","#FFE066","#7FB5A3","#D48BC0","#8FE3C0","#FFFFFF"));
        list.add(t("re-sunset", "RE Sunset", "#FFE8D6", "#1E1210", "#FFB35C",
            "#1E1210","#FF6B5B","#A8C686","#FFD166","#6A8EAE","#C77DFF","#5EC8C0","#E8DCC8",
            "#5A4238","#FF8A75","#C4E39B","#FFE066","#8FB3D9","#E0A3FF","#7FE3DB","#FFFFFF"));
        list.add(t("re-mono", "RE Mono", "#E8E8E8", "#101010", "#E8E8E8",
            "#101010","#A8A8A8","#B8B8B8","#C8C8C8","#989898","#A8A8A8","#B8B8B8","#D8D8D8",
            "#585858","#C8C8C8","#D8D8D8","#E8E8E8","#B8B8B8","#C8C8C8","#D8D8D8","#FFFFFF"));
        list.add(t("re-ocean", "RE Ocean", "#D6EAF8", "#081826", "#4FC3F7",
            "#081826","#EF6A6A","#7FC98F","#F2C14E","#4F9CF9","#B07FE8","#4FC3F7","#B0C4DE",
            "#2E4A5E","#FF8A80","#A5E8B0","#FFE082","#82B4FF","#CEA6FF","#80DEEA","#FFFFFF"));
        return Collections.unmodifiableList(list);
    }

    @NonNull public static TermuxTheme defaultTheme() { return all().get(0); }
}
