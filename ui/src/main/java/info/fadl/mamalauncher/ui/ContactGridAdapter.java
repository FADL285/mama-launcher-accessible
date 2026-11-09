package info.fadl.mamalauncher.ui;

import info.fadl.mamalauncher.ui.ContactEntry;
import info.fadl.mamalauncher.ui.UiBinder;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class ContactGridAdapter extends BaseAdapter {

    /** Calm, high-contrast palette for name tiles (hash-picked, stable per person). */
    private static final int[] TILE_COLORS = {
            0xFF2F5D8A, 0xFF6B4E8E, 0xFF8A5A2F, 0xFF2F7E6E, 0xFF8A2F4E, 0xFF4E6B2F
    };

    private final LayoutInflater inflater;
    private final ContactPhotoLoader photos;
    private final List<ContactEntry> contacts;

    public ContactGridAdapter(Context context, List<ContactEntry> contacts, ContactPhotoLoader photos) {
        this.inflater = LayoutInflater.from(context);
        this.contacts = contacts;
        this.photos = photos;
    }

    public static int tileColorFor(long contactId) {
        return TILE_COLORS[(int) (Math.abs(contactId) % TILE_COLORS.length)];
    }

    @Override public int getCount() { return contacts.size(); }
    @Override public Object getItem(int position) { return contacts.get(position); }
    @Override public long getItemId(int position) { return contacts.get(position).id; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView != null ? convertView
                : inflater.inflate(R.layout.item_contact, parent, false);
        ContactEntry c = contacts.get(position);

        ImageView photo = v.findViewById(R.id.photo);
        TextView fallback = v.findViewById(R.id.photo_fallback_name);
        TextView name = v.findViewById(R.id.name);
        ImageView star = v.findViewById(R.id.star);

        name.setText(c.name.isEmpty() ? v.getContext().getString(R.string.no_name) : c.name);
        star.setVisibility(c.starred ? View.VISIBLE : View.GONE);

        Bitmap bmp = photos.load(c.photoUri, 360);
        if (bmp != null) {
            photo.setImageBitmap(bmp);
            photo.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
        } else {
            photo.setImageDrawable(null);
            photo.setVisibility(View.GONE);
            fallback.setBackgroundColor(tileColorFor(c.id));
            fallback.setText(c.name);
            fallback.setVisibility(View.VISIBLE);
        }
        return v;
    }
}
