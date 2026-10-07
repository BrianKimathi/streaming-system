import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../core/utils/maturity.dart';
import '../../data/models/profile_models.dart';
import '../../widgets/profile_avatar.dart';
import '../../widgets/state_views.dart';

const _languages = <String, String>{
  'en': 'English',
  'sw': 'Kiswahili',
  'fr': 'Français',
  'es': 'Español',
  'pt': 'Português',
  'de': 'Deutsch',
  'ar': 'العربية',
};

/// Settings of the currently selected profile, saved with `PUT /profiles/{id}`.
class ProfileSettingsScreen extends ConsumerWidget {
  const ProfileSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(currentProfileProvider);
    if (profile == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Profile settings')),
        body: const EmptyView(icon: Icons.person_off_outlined, title: 'No profile selected'),
      );
    }
    return _SettingsForm(key: ValueKey(profile.id), profile: profile);
  }
}

class _SettingsForm extends ConsumerStatefulWidget {
  const _SettingsForm({super.key, required this.profile});

  final Profile profile;

  @override
  ConsumerState<_SettingsForm> createState() => _SettingsFormState();
}

class _SettingsFormState extends ConsumerState<_SettingsForm> {
  late bool _autoplay;
  late String _maturity;
  late String _language;
  late bool _pinOn;
  bool _changePin = false;
  final _pin = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _saving = false;
  String? _error;

  Profile get p => widget.profile;

  @override
  void initState() {
    super.initState();
    _reset(p);
  }

  void _reset(Profile profile) {
    _autoplay = profile.autoplayNext;
    _maturity = profile.maturityRating ?? (profile.isKids ? 'PG' : 'TV_MA');
    _language = profile.language ?? 'en';
    _pinOn = profile.pinProtected;
    _changePin = false;
    _pin.clear();
  }

  @override
  void dispose() {
    _pin.dispose();
    super.dispose();
  }

  bool get _needsPin => _pinOn && (!p.pinProtected || _changePin);

  Future<void> _save() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final changes = <String, dynamic>{};
    if (_autoplay != p.autoplayNext) changes['autoplayNext'] = _autoplay;
    if (!p.isKids && _maturity != p.maturityRating) changes['maturityRating'] = _maturity;
    if (_language != (p.language ?? 'en')) changes['language'] = _language;
    if (_pinOn != p.pinProtected || _needsPin) {
      changes['pinProtected'] = _pinOn;
      if (_needsPin) changes['pin'] = _pin.text;
    }
    if (changes.isEmpty) {
      showMessage(context, 'Nothing to save.');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final updated = await ref.read(profileRepositoryProvider).update(p.id, changes);
      ref.read(sessionControllerProvider.notifier).profileUpdated(updated);
      if (!mounted) return;
      setState(() => _reset(updated));
      showMessage(context, 'Profile settings saved.');
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final languages = {..._languages, if (!_languages.containsKey(_language)) _language: _language};
    return Scaffold(
      appBar: AppBar(title: const Text('Profile settings')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Row(
              children: [
                ProfileAvatar(avatarUrl: p.avatarUrl, name: p.name, size: 48),
                const SizedBox(width: 12),
                Text(p.name, style: Theme.of(context).textTheme.titleLarge),
                if (p.isKids) ...[
                  const SizedBox(width: 8),
                  const Chip(label: Text('Kids'), visualDensity: VisualDensity.compact),
                ],
              ],
            ),
            const SizedBox(height: 16),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Autoplay next episode'),
              subtitle: const Text('Start the next episode automatically after a countdown'),
              value: _autoplay,
              onChanged: (v) => setState(() => _autoplay = v),
            ),
            const SizedBox(height: 8),
            if (!p.isKids)
              DropdownButtonFormField<String>(
                initialValue: profileMaturityValues.contains(_maturity) ? _maturity : 'TV_MA',
                decoration: const InputDecoration(labelText: 'Maximum maturity rating'),
                items: [
                  for (final r in profileMaturityValues)
                    DropdownMenuItem(value: r, child: Text(displayRating(r))),
                ],
                onChanged: (v) => setState(() => _maturity = v ?? _maturity),
              )
            else
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Maturity rating'),
                subtitle: Text(
                  '${displayRating(_maturity)} — change it from Manage profiles on the profile picker.',
                ),
              ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              initialValue: _language,
              decoration: const InputDecoration(labelText: 'Display language'),
              items: [
                for (final e in languages.entries)
                  DropdownMenuItem(value: e.key, child: Text(e.value)),
              ],
              onChanged: (v) => setState(() => _language = v ?? _language),
            ),
            const SizedBox(height: 12),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Profile lock'),
              subtitle: const Text('Require a 4-digit PIN to open this profile'),
              value: _pinOn,
              onChanged: (v) => setState(() {
                _pinOn = v;
                _changePin = false;
              }),
            ),
            if (_pinOn && p.pinProtected && !_changePin)
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: () => setState(() => _changePin = true),
                  child: const Text('Change PIN'),
                ),
              ),
            if (_needsPin)
              TextFormField(
                controller: _pin,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 4,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: const InputDecoration(labelText: 'New 4-digit PIN', counterText: ''),
                validator: (v) =>
                    RegExp(r'^\d{4}$').hasMatch(v ?? '') ? null : 'The PIN must be exactly 4 digits',
              ),
            if (_error != null) ...[
              const SizedBox(height: 14),
              Text(_error!, style: const TextStyle(color: Color(0xFFF87171))),
            ],
            const SizedBox(height: 24),
            FilledButton(
              onPressed: _saving ? null : _save,
              child: _saving
                  ? const SizedBox(
                      width: 22,
                      height: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
                    )
                  : const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }
}
