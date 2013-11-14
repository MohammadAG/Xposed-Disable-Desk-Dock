package com.mohammadag.disabledeskdock;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class DisableDock implements IXposedHookZygoteInit {
	private UiModeManager mUiModeManager = null;
	private boolean mIsInCarMode = false;

	@Override
	public void initZygote(StartupParam startupParam) throws Throwable {
		findAndHookMethod("com.android.server.UiModeManagerService", null,
				"isDeskDockState", int.class, XC_MethodReplacement.returnConstant(false));

		Class<?> DockObserver = findClass("com.android.server.DockObserver", null);

		XposedBridge.hookAllConstructors(DockObserver, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
				mUiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);

				IntentFilter iF = new IntentFilter();
				iF.addAction(UiModeManager.ACTION_ENTER_CAR_MODE);
				iF.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);

				context.registerReceiver(new BroadcastReceiver() {
					@Override
					public void onReceive(Context context, Intent intent) {
						if (UiModeManager.ACTION_ENTER_CAR_MODE.equals(intent.getAction())) {
							mIsInCarMode = true;
						} else if (UiModeManager.ACTION_EXIT_CAR_MODE.equals(intent.getAction())) {
							mIsInCarMode = false;
						}
					}
				}, iF);
			}
		});

		findAndHookMethod(DockObserver, "handleDockStateChange", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				int mDockState = getIntField(param.thisObject, "mDockState");
				// int mPreviousDockState = getIntField(param.thisObject, "mPreviousDockState");

				int newFakeState = Intent.EXTRA_DOCK_STATE_UNDOCKED;
				switch (mDockState) {
				case Intent.EXTRA_DOCK_STATE_DESK:
				case Intent.EXTRA_DOCK_STATE_HE_DESK:
				case Intent.EXTRA_DOCK_STATE_LE_DESK:
				default:
					newFakeState = Intent.EXTRA_DOCK_STATE_UNDOCKED;
					break;
				case Intent.EXTRA_DOCK_STATE_CAR:
					newFakeState = Intent.EXTRA_DOCK_STATE_CAR;
					break;
				}
				if (mUiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR || mIsInCarMode) {
					newFakeState = Intent.EXTRA_DOCK_STATE_CAR;
					setIntField(param.thisObject, "mDockState", newFakeState);
					/* Prevent sound playback */
					param.setResult(null);
				}

				/*
				String debugString = String.format("New: %s\nPrevious: %s\nFake: %s\nCar?: %s",
						getStringFromDockState(mDockState), getStringFromDockState(mPreviousDockState),
						getStringFromDockState(newFakeState), String.valueOf(mIsInCarMode));

				Toast.makeText(mContext, debugString, Toast.LENGTH_LONG).show();
				 */

				setIntField(param.thisObject, "mDockState", newFakeState);
			}
		});
	}
	/*
	private static String getStringFromDockState(int dockState) {
		switch (dockState) {
		case Intent.EXTRA_DOCK_STATE_DESK:
			return "Intent.EXTRA_DOCK_STATE_DESK";
		case Intent.EXTRA_DOCK_STATE_HE_DESK:
			return "Intent.EXTRA_DOCK_STATE_HE_DESK";
		case Intent.EXTRA_DOCK_STATE_LE_DESK:
			return "Intent.EXTRA_DOCK_STATE_LE_DESK";
		case Intent.EXTRA_DOCK_STATE_CAR:
			return "Intent.EXTRA_DOCK_STATE_CAR";
		case Intent.EXTRA_DOCK_STATE_UNDOCKED:
		default:
			return "Intent.EXTRA_DOCK_STATE_UNDOCKED";
		}
	}
	 */
}
