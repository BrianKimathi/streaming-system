import 'dart:io' show Platform;

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/widgets.dart';
import 'package:package_info_plus/package_info_plus.dart';

class DeviceIdentity {
  const DeviceIdentity({
    required this.name,
    required this.type,
    required this.platform,
    required this.appVersion,
  });

  final String name;

  /// PHONE or TABLET (shortest logical side >= 600 dp is a tablet).
  final String type;

  /// "android" or "ios".
  final String platform;
  final String appVersion;

  static Future<DeviceIdentity> current() async {
    final info = DeviceInfoPlugin();
    String name;
    String platform;
    if (Platform.isIOS) {
      platform = 'ios';
      final ios = await info.iosInfo;
      name = ios.modelName.isNotEmpty ? ios.modelName : ios.model;
    } else {
      platform = 'android';
      final android = await info.androidInfo;
      final maker = android.manufacturer.trim();
      final model = android.model.trim();
      name = model.toLowerCase().startsWith(maker.toLowerCase()) || maker.isEmpty
          ? model
          : '${maker[0].toUpperCase()}${maker.substring(1)} $model';
    }

    String version;
    try {
      final pkg = await PackageInfo.fromPlatform();
      version = pkg.buildNumber.isEmpty ? pkg.version : '${pkg.version}+${pkg.buildNumber}';
    } on Object {
      version = 'unknown';
    }

    return DeviceIdentity(
      name: name.isEmpty ? 'Mobile device' : name,
      type: _isTablet() ? 'TABLET' : 'PHONE',
      platform: platform,
      appVersion: version,
    );
  }

  static bool _isTablet() {
    final views = WidgetsBinding.instance.platformDispatcher.views;
    if (views.isEmpty) return false;
    final view = views.first;
    final size = view.physicalSize / view.devicePixelRatio;
    return size.shortestSide >= 600;
  }
}
