package com.example.babyapp;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

// Adapter for holding devices found through scanning.
public class LeDeviceListAdapter extends BaseAdapter {
  private ArrayList<BluetoothDevice> mLeDevices;
  private LayoutInflater inflater;
  private int indentionBase;         //資料間距

  Context c;
  public LeDeviceListAdapter(Context c) {
    super();
    this.c = c;
    mLeDevices = new ArrayList<BluetoothDevice>();
    this.inflater = inflater;
    indentionBase = 50;
  }

  public void addDevice(BluetoothDevice device) {
    if(!mLeDevices.contains(device)) {
      mLeDevices.add(device);
    }
  }

  public BluetoothDevice getDevice(int position) {
    return mLeDevices.get(position);
  }

  public void clear() {
    mLeDevices.clear();
  }

  @Override
  public int getCount() {
    return mLeDevices.size();
  }

  @Override
  public Object getItem(int i) {
    return mLeDevices.get(i);
  }

  @Override
  public long getItemId(int i) {
    return i;
  }

  @Override
  public View getView(int position, View view, ViewGroup viewGroup) {
    ViewHolder viewHolder;
    // General ListView optimization code.
    //初次載入，避免重複初始化
    if (view == null) {
      LayoutInflater inflater = (LayoutInflater) c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      view = inflater.inflate(R.layout.listview, null);
      viewHolder = new ViewHolder();
      viewHolder.deviceAddress = (TextView) view.findViewById(R.id.tv_address);
      viewHolder.deviceName = (TextView) view.findViewById(R.id.tv_Name);
      view.setTag(viewHolder);
      viewHolder.rlBorder = (LinearLayout) view.findViewById(R.id.llBorder);
    } else {
      viewHolder = (ViewHolder) view.getTag();
    }

    BluetoothDevice device = mLeDevices.get(position);
    final String deviceName = device.getName();
    if (deviceName != null && deviceName.length() > 0)
      viewHolder.deviceName.setText(deviceName);
    else
      viewHolder.deviceName.setText("unknown_device");

    viewHolder.deviceAddress.setText(device.getAddress());
    //設定layout參數
    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) viewHolder.rlBorder.getLayoutParams();
    lp.setMargins(indentionBase,0, 0,0);//縮排

    return view;
  }
}

class ViewHolder {
  LinearLayout rlBorder;
  TextView deviceName;
  TextView deviceAddress;
}
