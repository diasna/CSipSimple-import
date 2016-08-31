package com.csipsimple.ui.extension;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.csipsimple.R;

/**
 * Created by islamap.Inc on 4/24/15.
 * Copyright (c) 2014 Machikon. All rights reserved.
 */
public class ContactAdapter extends ArrayAdapter<Contact> {
    private final Activity context;
    private final Contact[] contacts;

    static class ViewHolder {
        public TextView text;
        public ImageView image;
    }

    public ContactAdapter(Activity context, Contact[] contacts) {
        super(context, R.layout.extension_main_list_row, contacts);
        this.context = context;
        this.contacts = contacts;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View rowView = convertView;

        if (rowView == null) {
            LayoutInflater inflater = context.getLayoutInflater();
            rowView = inflater.inflate(R.layout.extension_main_list_row, null);

            ViewHolder viewHolder = new ViewHolder();
            viewHolder.text = (TextView) rowView.findViewById(R.id.textView3);
            viewHolder.image = (ImageView) rowView.findViewById(R.id.imageView);
            rowView.setTag(viewHolder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();
        Contact s = getItem(position);
        holder.text.setText(s.getName());
        holder.image.setImageResource(R.drawable.no_image);
        return rowView;
    }

    @Override
    public Contact getItem(int position) {
        return contacts[position];
    }

}