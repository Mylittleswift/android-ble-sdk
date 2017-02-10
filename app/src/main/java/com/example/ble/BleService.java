package com.example.b018test;import java.lang.reflect.Method;import java.util.ArrayList;import java.util.HashMap;import java.util.List;import java.util.UUID;import android.app.Service;import android.bluetooth.BluetoothAdapter;import android.bluetooth.BluetoothDevice;import android.bluetooth.BluetoothGatt;import android.bluetooth.BluetoothGattCallback;import android.bluetooth.BluetoothGattCharacteristic;import android.bluetooth.BluetoothGattDescriptor;import android.bluetooth.BluetoothGattService;import android.bluetooth.BluetoothManager;import android.bluetooth.BluetoothProfile;import android.content.Context;import android.content.Intent;import android.os.Binder;import android.os.Handler;import android.os.IBinder;import android.support.v4.content.LocalBroadcastManager;import android.util.Log;public final class BleService extends Service {	private static final UUID NOTIY = UUID			.fromString("00002902-0000-1000-8000-00805f9b34fb");	private static final UUID DATA = UUID			.fromString("0000fff6-0000-1000-8000-00805f9b34fb");	private static final UUID CHANGE = UUID			.fromString("0000fff7-0000-1000-8000-00805f9b34fb");	private static final UUID SERVICE = UUID			.fromString("0000fff0-0000-1000-8000-00805f9b34fb");	private BluetoothGattCharacteristic dataGattCharacteristic;	public final static String ACTION_GATT_CONNECTED = "com.jstyle.ble.service.ACTION_GATT_CONNECTED";	public final static String ACTION_GATT_CONNECTING = "com.jstyle.ble.service.ACTION_GATT_CONNECTING";	public final static String ACTION_GATT_DISCONNECTED = "com.jstyle.ble.service.ACTION_GATT_DISCONNECTED";	public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.jstyle.ble.service.ACTION_GATT_SERVICES_DISCOVERED";	public final static String ACTION_DATA_AVAILABLE = "com.jstyle.ble.service.ACTION_DATA_AVAILABLE";	public final static String EXTRA_DATA = "com.jstyle.ble.service.EXTRA_DATA";	public final static String ACTION_GAT_RSSI = "com.jstyle.ble.service.RSSI";	public final static String ACTION_GATT_ENABLENOTIY = "com.jstyle.ble.service.ACTION_GATT_ENABLENOTIY"; // 唯一标识	public HashMap<BluetoothDevice, BluetoothGatt> hasp = new HashMap<BluetoothDevice, BluetoothGatt>();	private final IBinder kBinder = new LocalBinder();	protected List<BluetoothGattService> services;	// private BluetoothGatt gatt = null;	private static ArrayList<BluetoothGatt> arrayGatts = new ArrayList<BluetoothGatt>(); // 存放BluetoothGatt的集�??	private BluetoothAdapter bluetoothAdapter = BluetoothAdapter			.getDefaultAdapter();	public static BluetoothGattCharacteristic colorCharacteristic;	private HashMap<String, BluetoothGatt> gattHash = new HashMap<String, BluetoothGatt>();	private BluetoothManager bluetoothManager;	private BluetoothAdapter mBluetoothAdapter;	private BluetoothGatt mgatt;	private Handler handler = new Handler();	@Override	public IBinder onBind(Intent intent) {		initAdapter();		return kBinder;	}	@Override	public boolean onUnbind(Intent intent) {		disconnect();		return super.onUnbind(intent);	}	/**	 * 初始化BLE 如果已经连接就不用再次连	 * 	 * @param bleDevice	 * @return	 */	private long time;	public synchronized boolean initBluetoothDevice(final String address,			final Context context) {		if (mgatt != null)			return true;		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);		mgatt = device.connectGatt(context, false, bleGattCallback);		if (mgatt == null) {			System.out.println(device.getAddress() + "gatt is null");		}		return true;	}	private void initAdapter() {		if (bluetoothManager == null) {			bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);			if (bluetoothManager == null) {				return;			}		}		mBluetoothAdapter = bluetoothManager.getAdapter();	}	/**	 * 断开连接	 */	public void disconnect() {		if (mgatt == null)			return;		mgatt.disconnect();	}	/**	 * 根据设备的Mac地址断开连接	 * 	 * @param address	 */	public void disconnect(String address) {		ArrayList<BluetoothGatt> gatts = new ArrayList<BluetoothGatt>();		for (BluetoothGatt gatt : arrayGatts) {			if (gatt != null && gatt.getDevice().getAddress().equals(address)) {				gatts.add(gatt);				// gatt.disconnect();				gatt.close();				// gatt = null;			}		}		arrayGatts.removeAll(gatts);	}	public class LocalBinder extends Binder {		public BleService getService() {			return BleService.this;		}	}	private int discoverCount;	private boolean isConnect;	private BluetoothGattCallback bleGattCallback = new BluetoothGattCallback() {		/*		 * 连接的状发生变化 (non-Javadoc)		 * 		 * @see		 * android.bluetooth.BluetoothGattCallback#onConnectionStateChange(android		 * .bluetooth.BluetoothGatt, int, int)		 */		@Override		public void onConnectionStateChange(BluetoothGatt gatt, int status,				int newState) {			String action = null;			if (newState == BluetoothProfile.STATE_CONNECTED) {				if (status == 133) {					mgatt.close();					mgatt = null;					return;				}				isConnect = true;				action = ACTION_GATT_CONNECTED;				try {					gatt.discoverServices();				} catch (Exception e) {				}			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {				isConnect = false;				action = ACTION_GATT_DISCONNECTED;				broadcastUpdate(action);				if (mgatt != null) {					mgatt.close();					mgatt = null;				}			}		}		/*		 * 搜索device中的services (non-Javadoc)		 * 		 * @see		 * android.bluetooth.BluetoothGattCallback#onServicesDiscovered(android		 * .bluetooth.BluetoothGatt, int)		 */		@Override		public void onServicesDiscovered(BluetoothGatt gatt, int status) {			if (mgatt == null)				return;			if (status == BluetoothGatt.GATT_SUCCESS) {				String address = gatt.getDevice().getAddress();				String name = mBluetoothAdapter.getRemoteDevice(address)						.getName();				BluetoothGattService service = gatt.getService(SERVICE);				if (service == null)					return;				dataGattCharacteristic = service.getCharacteristic(DATA);				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);				setCharacteristicNotification(						service.getCharacteristic(CHANGE), true);				discoverCount = 0;			} else {				if (discoverCount++ < 3) {					gatt.discoverServices();				}				mgatt = null;				Log.w("servieDiscovered", "onServicesDiscovered received: "						+ status);			}		}		/*		 * 读取特征�??(non-Javadoc)		 * 		 * @see		 * android.bluetooth.BluetoothGattCallback#onCharacteristicRead(android		 * .bluetooth.BluetoothGatt,		 * android.bluetooth.BluetoothGattCharacteristic, int)		 */		public void onCharacteristicRead(BluetoothGatt gatt,				android.bluetooth.BluetoothGattCharacteristic characteristic,				int status) {			if (status == BluetoothGatt.GATT_SUCCESS) {				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt						.getDevice().getAddress());			} else {				Log.i("onCharacteristicRead", "onCharacteristicRead false "						+ status + characteristic.toString());			}		}		public void onDescriptorWrite(BluetoothGatt gatt,				BluetoothGattDescriptor descriptor, int status) {			if (status == BluetoothGatt.GATT_SUCCESS) {				broadcastUpdate(ACTION_GATT_ENABLENOTIY);			}		};		/*		 * 特征值的变化 (non-Javadoc)		 * 		 * @see		 * android.bluetooth.BluetoothGattCallback#onCharacteristicChanged(android		 * .bluetooth.BluetoothGatt,		 * android.bluetooth.BluetoothGattCharacteristic)		 */		public void onCharacteristicChanged(BluetoothGatt gatt,				android.bluetooth.BluetoothGattCharacteristic characteristic) {			System.out.println("notify"					+ Tools.byte2Hex(characteristic.getValue()));			broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt					.getDevice().getAddress());		}		public void onCharacteristicWrite(BluetoothGatt gatt,				BluetoothGattCharacteristic characteristic, int status) {			if (status == BluetoothGatt.GATT_SUCCESS) {				System.out.println(Tools.byte2Hex(characteristic.getValue()));			} else {			}		};	};	public boolean refreshDeviceCache(BluetoothGatt gatt) {		try {			BluetoothGatt localBluetoothGatt = gatt;			Method localMethod = localBluetoothGatt.getClass().getMethod(					"refresh", new Class[0]);			if (localMethod != null) {				boolean bool = ((Boolean) localMethod.invoke(						localBluetoothGatt, new Object[0])).booleanValue();				return bool;			}		} catch (Exception localException) {			Log.e("s", "An exception occured while refreshing device");		}		return false;	}	/**	 * 广播	 * 	 * @param action	 */	private void broadcastUpdate(String action) {		final LocalBroadcastManager broadcastManager = LocalBroadcastManager				.getInstance(this);		Intent intent = new Intent(action);		broadcastManager.sendBroadcast(intent);	}	protected void broadcastUpdate(String action,			BluetoothGattCharacteristic characteristic) {		final LocalBroadcastManager broadcastManager = LocalBroadcastManager				.getInstance(this);		// TODO Auto-generated method stub		Intent intent = new Intent(action);		final byte[] data = characteristic.getValue();		if (data != null && data.length > 0) {			intent.putExtra(EXTRA_DATA, characteristic.getValue());		}		broadcastManager.sendBroadcast(intent);	}	/**	 * 发�?带蓝牙信息的到广�?? *	 * 	 * @param action	 * @param characteristic	 */	private void broadcastUpdate(String action,			BluetoothGattCharacteristic characteristic, String mac) {		final LocalBroadcastManager broadcastManager = LocalBroadcastManager				.getInstance(this);		Intent intent = new Intent(action);		intent.putExtra(EXTRA_DATA, characteristic.getValue());		broadcastManager.sendBroadcast(intent);	}	/**	 * 读取设备数据	 * 	 * @param device	 * @param characteristic	 */	public void readValue(BluetoothDevice device,			BluetoothGattCharacteristic characteristic) {		// TODO Auto-generated method stub		if (mgatt == null) {			return;		}		try {			Thread.sleep(15);		} catch (InterruptedException e) {			// TODO Auto-generated catch block			e.printStackTrace();		}		mgatt.readCharacteristic(characteristic);	}	/**	 * 写入设备数据	 * 	 * @param device	 * @param characteristic	 */	public void writeValue(BluetoothDevice device,			BluetoothGattCharacteristic characteristic) {		// TODO Auto-generated method stub		// characteristic=gatt.getService(UUID.fromString("")).getCharacteristic(UUID.fromString(""));		if (mgatt == null) {			return;		}		byte[] value = characteristic.getValue();		if (value == null) {			return;		}		mgatt.writeCharacteristic(characteristic);	}	public void setCharacteristicNotification(			BluetoothGattCharacteristic characteristic, boolean enable) {		// TODO Auto-generated method stub		if (mgatt == null) {			return;		}		mgatt.setCharacteristicNotification(characteristic, enable);		try {			Thread.sleep(20);		} catch (InterruptedException e) {			e.printStackTrace();		}		BluetoothGattDescriptor descriptor = characteristic				.getDescriptor(NOTIY);		if (descriptor == null) {			return;		}		descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);		mgatt.writeDescriptor(descriptor);	}	public List<BluetoothGattService> getSupportedGattServices() {		if (mgatt == null) {			return null;		}		return mgatt.getServices();	}	/**	 * 根据设备的Mac地址从已经连接的设备中匹配对应的BluetoothGatt对象	 * 	 * @param device	 * @return	 */	private BluetoothGatt getBluetoothGatt(BluetoothDevice device) {		return mgatt;	}	public void writeValue(byte[] value) {		if (dataGattCharacteristic == null || mgatt == null)			return;		dataGattCharacteristic.setValue(value);		mgatt.writeCharacteristic(dataGattCharacteristic);	}	/**	 * 	 //读取信号	 * 	 * @param device	 */	public void readRssi(BluetoothDevice device) {		mgatt.readRemoteRssi();	}	@Override	public void onDestroy() {		// TODO Auto-generated method stub		super.onDestroy();		bleGattCallback = null;	}}