package info.fadl.mamalauncher;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Which app shortcuts appear on mama's everyday home screen, chosen AND
 *  ordered by the family setter in Settings. The special Phone button is not
 *  part of this list; it is always present. No apps are imposed by default:
 *  an unset/empty selection means no app tiles. Pure string logic, pinned by
 *  AppSelectionTest. */
public final class AppSelection {

    public static final String PREF_KEY = "apps_selected";
    public static final String HOME_ORDER_KEY="home_item_order";
    public static final String PHONE="@phone", FOLDER="@apps";
    /** Keep stored relative positions, migrate old app-only settings, never drop unavailable packages. */
    public static List<String> homeOrder(String stored,List<String> selected) {
        List<String> allowed=new ArrayList<>();allowed.add(PHONE);allowed.add(FOLDER);allowed.addAll(selected);
        List<String> out=parse(stored);out.retainAll(allowed);
        for(String item:allowed)if(!out.contains(item))out.add(item);
        return out;
    }
    public static final String SECONDARY_KEY = "apps_secondary";

    private AppSelection() {}

    /** Parses the stored comma-separated ordered selection; null/empty = none. */
    public static List<String> parse(String stored) {
        List<String> out = new ArrayList<>();
        if (stored == null || stored.trim().isEmpty()) return out;
        for (String p : stored.split(",")) {
            String pkg = p.trim();
            if (!pkg.isEmpty() && !out.contains(pkg)) out.add(pkg);
        }
        return out;
    }

    /** Serializes a selection: duplicates removed, order preserved. */
    public static String join(List<String> pkgs) {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String p : pkgs) {
            if (p != null && !p.trim().isEmpty()) clean.add(p.trim());
        }
        StringBuilder sb = new StringBuilder();
        for (String p : clean) {
            if (sb.length() > 0) sb.append(',');
            sb.append(p);
        }
        return sb.toString();
    }

    /** Move to an arbitrary drop/tap destination without mutating the input. */
    public static List<String> moveTo(List<String> pkgs,int from,int to) {
        List<String> out=new ArrayList<>(pkgs);
        if(from<0 || from>=out.size() || to<0 || to>=out.size())return out;
        out.add(to,out.remove(from));return out;
    }

    /** Moves the entry at index i one step up (delta -1) or down (delta +1);
     *  returns the same list untouched when the move is out of range. */
    public static List<String> move(List<String> pkgs, int i, int delta) {
        List<String> out = new ArrayList<>(pkgs);
        int j = i + delta;
        if (i < 0 || i >= out.size() || j < 0 || j >= out.size()) return out;
        String t = out.get(i);
        out.set(i, out.get(j));
        out.set(j, t);
        return out;
    }
}
