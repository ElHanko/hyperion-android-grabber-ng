package com.elhanko.hyperiongrabber.ng.common.discovery;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.elhanko.hyperiongrabber.ng.common.R;

import java.net.Inet6Address;
import java.util.ArrayList;
import java.util.List;

/** Small shared list adapter for explicit TV and mobile server selection. */
public final class DiscoveredServerAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<DiscoveredHyperionServer> servers = new ArrayList<>();

    public DiscoveredServerAdapter(@NonNull Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void replace(@NonNull List<DiscoveredHyperionServer> replacements) {
        servers.clear();
        servers.addAll(replacements);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return servers.size();
    }

    @Override
    public DiscoveredHyperionServer getItem(int position) {
        return servers.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getStableKey().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {
            row = inflater.inflate(android.R.layout.simple_list_item_2, parent, false);
        }
        DiscoveredHyperionServer server = getItem(position);
        TextView title = row.findViewById(android.R.id.text1);
        TextView details = row.findViewById(android.R.id.text2);
        title.setText(server.getDisplayName());

        String host = server.getHostAddress() instanceof Inet6Address
                ? "[" + server.getHostAddressText() + "]"
                : server.getHostAddressText();
        if (server.getHyperionVersion() == null) {
            details.setText(parent.getContext().getString(
                    R.string.discovery_server_details,
                    host,
                    server.getProtoServerPort()));
        } else {
            details.setText(parent.getContext().getString(
                    R.string.discovery_server_details_with_version,
                    host,
                    server.getProtoServerPort(),
                    server.getHyperionVersion()));
        }
        return row;
    }
}
