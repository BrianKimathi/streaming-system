import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/session/session_controller.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/maturity.dart';
import '../../data/models/profile_models.dart';
import '../../widgets/profile_avatar.dart';
import '../../widgets/state_views.dart';
import 'profile_picker_screen.dart';

/// Create (existing == null) or edit a profile.
class ProfileEditorScreen extends ConsumerStatefulWidget {
  const ProfileEditorScreen({super.key, this.existing});

  final Profile? existing;

  @override
  ConsumerState<ProfileEditorScreen> createState() => _ProfileEditorScreenState();
}

class _ProfileEditorScreenState extends ConsumerState<ProfileEditorScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name;
  final _pin = TextEditingController();
  late int _avatar;
  late bool _kids;
  late String _maturity;
  late bool _pinOn;
  bool _changePin = false;
  bool _saving = false;
  String? _error;

  bool get _isEdit => widget.existing != null;

  @override
  void initState() {
    super.initState();
    final p = widget.existing;
    _name = TextEditingController(text: p?.name ?? '');
    _avatar = AvatarPalette.indexOf(p?.avatarUrl) ?? 0;
    _kids = p?.isKids ?? false;
    _maturity = p?.maturityRating ?? (_kids ? 'PG' : 'TV_MA');
    _pinOn = p?.pinProtected ?? false;
  }

  @override
  void dispose() {
    _name.dispose();
    _pin.dispose();
    super.dispose();
  }

  List<String> get _maturityOptions => _kids ? kidsMaturityValues : profileMaturityValues;

  bool get _needsPinInput => _pinOn && (!_isEdit || !widget.existing!.pinProtected || _changePin);

  Future<void> _save() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    final repo = ref.read(profileRepositoryProvider);
    try {
      if (!_isEdit) {
        await repo.create(
          name: _name.text,
          avatarUrl: AvatarPalette.value(_avatar),
          kids: _kids,
          maturityRating: _maturity,
          pin: _pinOn ? _pin.text : null,
        );
      } else {
        final existing = widget.existing!;
        final changes = <String, dynamic>{
          'name': _name.text.trim(),
          'avatarUrl': AvatarPalette.value(_avatar),
          'maturityRating': _maturity,
        };
        if (_pinOn != existing.pinProtected || _needsPinInput) {
          changes['pinProtected'] = _pinOn;
          if (_needsPinInput) changes['pin'] = _pin.text;
        }
        final updated = await repo.update(existing.id, changes);
        ref.read(sessionControllerProvider.notifier).profileUpdated(updated);
      }
      ref.invalidate(profilesProvider);
      if (mounted) context.pop();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _delete() async {
    final existing = widget.existing!;
    final ok = await confirm(
      context,
      title: 'Delete profile?',
      message: "${existing.name}'s watch history and My List will be permanently deleted.",
      confirmLabel: 'Delete',
      destructive: true,
    );
    if (!ok || !mounted) return;
    setState(() => _saving = true);
    try {
      await ref.read(profileRepositoryProvider).delete(existing.id);
      await ref.read(sessionControllerProvider.notifier).profileDeleted(existing.id);
      ref.invalidate(profilesProvider);
      if (mounted) context.pop();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(_isEdit ? 'Edit profile' : 'Add profile')),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Center(child: ProfileAvatar(avatarUrl: AvatarPalette.value(_avatar), name: _name.text, size: 96)),
            const SizedBox(height: 20),
            TextFormField(
              controller: _name,
              maxLength: 30,
              textCapitalization: TextCapitalization.words,
              decoration: const InputDecoration(labelText: 'Name', counterText: ''),
              validator: (v) => (v == null || v.trim().isEmpty) ? 'Enter a name' : null,
            ),
            const SizedBox(height: 20),
            const Text('Avatar', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 10),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                for (var i = 0; i < AvatarPalette.count; i++)
                  GestureDetector(
                    onTap: () => setState(() => _avatar = i),
                    child: Container(
                      padding: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: i == _avatar ? Colors.white : Colors.transparent,
                          width: 2,
                        ),
                      ),
                      child: ProfileAvatar(avatarUrl: AvatarPalette.value(i), name: '', size: 48),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            if (!_isEdit)
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Kids profile'),
                subtitle: const Text('Only shows titles rated for children'),
                value: _kids,
                onChanged: (v) => setState(() {
                  _kids = v;
                  _maturity = v ? 'PG' : 'TV_MA';
                }),
              )
            else if (_kids)
              const ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.child_care_rounded),
                title: Text('Kids profile'),
              ),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              key: ValueKey(_kids),
              initialValue: _maturityOptions.contains(_maturity) ? _maturity : _maturityOptions.last,
              decoration: const InputDecoration(labelText: 'Maximum maturity rating'),
              items: [
                for (final r in _maturityOptions)
                  DropdownMenuItem(value: r, child: Text(displayRating(r))),
              ],
              onChanged: (v) => setState(() => _maturity = v ?? _maturity),
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
            if (_isEdit && _pinOn && widget.existing!.pinProtected && !_changePin)
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: () => setState(() => _changePin = true),
                  child: const Text('Change PIN'),
                ),
              ),
            if (_needsPinInput)
              TextFormField(
                controller: _pin,
                obscureText: true,
                keyboardType: TextInputType.number,
                maxLength: 4,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: const InputDecoration(labelText: '4-digit PIN', counterText: ''),
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
                  : Text(_isEdit ? 'Save' : 'Create profile'),
            ),
            if (_isEdit) ...[
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _saving ? null : _delete,
                style: OutlinedButton.styleFrom(foregroundColor: AppColors.accent),
                icon: const Icon(Icons.delete_outline_rounded),
                label: const Text('Delete profile'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
