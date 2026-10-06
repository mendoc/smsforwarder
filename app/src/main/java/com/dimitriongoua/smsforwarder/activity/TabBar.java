package com.dimitriongoua.smsforwarder.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.dimitriongoua.smsforwarder.R;

/**
 * Barre de navigation (layout {@code view_tab_bar}) : onglet actif en vert sur pastille, les
 * autres en gris. L'Accueil reste à la racine : un autre onglet s'ouvre par-dessus et
 * remplace l'onglet courant, le bouton Retour ramène donc toujours à l'Accueil.
 */
public final class TabBar {
    public enum Tab { HOME, JOURNAL, DESTINATIONS, SETTINGS }

    private TabBar() {
    }

    public static void bind(Activity activity, Tab current) {
        setUp(activity, current, Tab.HOME, R.id.tab_home, R.id.tab_home_pill, R.id.tab_home_icon, R.id.tab_home_label);
        setUp(activity, current, Tab.JOURNAL, R.id.tab_journal, R.id.tab_journal_pill, R.id.tab_journal_icon, R.id.tab_journal_label);
        setUp(activity, current, Tab.DESTINATIONS, R.id.tab_destinations, R.id.tab_destinations_pill,
                R.id.tab_destinations_icon, R.id.tab_destinations_label);
        setUp(activity, current, Tab.SETTINGS, R.id.tab_settings, R.id.tab_settings_pill, R.id.tab_settings_icon,
                R.id.tab_settings_label);
    }

    /** Ouvre un onglet depuis n'importe quel écran à onglets (bouton Réglages de l'Accueil…). */
    public static void open(Activity from, Tab current, Tab target) {
        if (target == current) return;
        if (target == Tab.HOME) {
            from.finish();
        } else {
            Intent intent = new Intent(from, activityOf(target));
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            from.startActivity(intent);
            if (current != Tab.HOME) from.finish();
        }
        from.overridePendingTransition(0, 0);
    }

    private static Class<? extends Activity> activityOf(Tab tab) {
        switch (tab) {
            case JOURNAL:
                return JournalActivity.class;
            case DESTINATIONS:
                return DestinationsActivity.class;
            case SETTINGS:
                return ReglagesActivity.class;
            default:
                return MainActivity.class;
        }
    }

    private static void setUp(Activity activity, Tab current, Tab tab, int itemId, int pillId, int iconId, int labelId) {
        View item = activity.findViewById(itemId);
        if (item == null) return;
        boolean active = tab == current;
        int color = ContextCompat.getColor(activity, active ? R.color.pine : R.color.ink_2);
        activity.findViewById(pillId).setBackgroundResource(active ? R.drawable.bg_tab_active : 0);
        ((ImageView) activity.findViewById(iconId)).setImageTintList(ColorStateList.valueOf(color));
        TextView label = activity.findViewById(labelId);
        label.setTextColor(color);
        label.setTypeface(ResourcesCompat.getFont(activity,
                active ? R.font.plex_sans_semibold : R.font.plex_sans_medium));
        item.setSelected(active);
        item.setContentDescription(label.getText());
        item.setOnClickListener(v -> open(activity, current, tab));
    }
}
