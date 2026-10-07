import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_exception.dart';
import '../../core/providers.dart';
import '../../core/theme/app_theme.dart';
import '../../core/utils/format.dart';
import '../../core/utils/phone.dart';
import '../../data/models/misc_models.dart';
import '../../data/models/subscription_models.dart';
import '../auth/validators.dart';
import 'account_providers.dart';

enum _Phase { form, waiting, completed, failed, timedOut }

/// M-Pesa STK push checkout: collect the phone number, start the checkout,
/// then poll the transaction until M-Pesa confirms or rejects it.
class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key, required this.plan});

  final Plan plan;

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  static const _pollEvery = Duration(seconds: 3);
  static const _maxWait = Duration(minutes: 3);

  final _formKey = GlobalKey<FormState>();
  final _phone = TextEditingController();
  bool _prefilled = false;
  bool _submitting = false;
  String? _error;

  _Phase _phase = _Phase.form;
  PaymentTransaction? _tx;
  Timer? _poll;
  DateTime? _startedAt;
  bool _polling = false;
  String? _pollWarning;

  @override
  void dispose() {
    _poll?.cancel();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final phone = normalizeKenyanPhone(_phone.text)!;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final tx = await ref
          .read(billingRepositoryProvider)
          .checkout(planId: widget.plan.id, phoneNumber: phone);
      if (!mounted) return;
      _tx = tx;
      _applyStatus(tx);
      if (tx.isPending) _startPolling();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _startPolling() {
    _startedAt = DateTime.now();
    _pollWarning = null;
    setState(() => _phase = _Phase.waiting);
    _poll?.cancel();
    _poll = Timer.periodic(_pollEvery, (_) => _checkOnce());
  }

  Future<void> _checkOnce() async {
    final tx = _tx;
    if (tx == null || _polling) return;
    _polling = true;
    try {
      final latest = await ref.read(billingRepositoryProvider).transaction(tx.id);
      if (!mounted) return;
      _tx = latest;
      _pollWarning = null;
      _applyStatus(latest);
    } on ApiException catch (e) {
      if (!mounted) return;
      if (e.isNotFound) {
        _poll?.cancel();
        setState(() {
          _phase = _Phase.failed;
          _error = e.message;
        });
        return;
      }
      setState(() => _pollWarning = e.message);
    } finally {
      _polling = false;
    }
    if (!mounted || _phase != _Phase.waiting) return;
    if (DateTime.now().difference(_startedAt ?? DateTime.now()) >= _maxWait) {
      _poll?.cancel();
      setState(() => _phase = _Phase.timedOut);
    }
  }

  void _applyStatus(PaymentTransaction tx) {
    if (tx.isCompleted) {
      _poll?.cancel();
      ref.invalidate(subscriptionProvider);
      ref.invalidate(billingHistoryProvider);
      setState(() => _phase = _Phase.completed);
    } else if (tx.isPending) {
      if (_phase != _Phase.waiting) setState(() => _phase = _Phase.waiting);
    } else {
      _poll?.cancel();
      setState(() {
        _phase = _Phase.failed;
        _error = tx.errorMessage ?? 'The payment was ${humanizeEnum(tx.status).toLowerCase()}.';
      });
    }
  }

  void _tryAgain() {
    _poll?.cancel();
    setState(() {
      _phase = _Phase.form;
      _tx = null;
      _error = null;
    });
  }

  Future<void> _checkAgain() async {
    setState(() {
      _phase = _Phase.waiting;
      _startedAt = DateTime.now().subtract(_maxWait - const Duration(seconds: 30));
    });
    _poll?.cancel();
    _poll = Timer.periodic(_pollEvery, (_) => _checkOnce());
    await _checkOnce();
  }

  @override
  Widget build(BuildContext context) {
    if (!_prefilled) {
      final phone = ref.watch(accountProvider).value?.phoneNumber;
      final normalized = normalizeKenyanPhone(phone);
      if (normalized != null) {
        _prefilled = true;
        _phone.text = formatKenyanPhone(normalized);
      }
    }
    final plan = widget.plan;
    return PopScope(
      canPop: _phase != _Phase.waiting,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _phase == _Phase.waiting) {
          showLeaveWarning(context);
        }
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Pay with M-Pesa')),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            Card(
              child: ListTile(
                title: Text(plan.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('Billed per ${intervalLabel(plan.billingInterval)}'),
                trailing: Text(
                  formatMoney(plan.price, plan.currency),
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                ),
              ),
            ),
            const SizedBox(height: 20),
            ..._body(context),
          ],
        ),
      ),
    );
  }

  void showLeaveWarning(BuildContext context) {
    showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Payment in progress'),
        content: const Text(
          'We are still waiting for M-Pesa to confirm your payment. '
          'If you leave now, your plan will still activate once the payment is confirmed.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: const Text('Stay'),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(dialogContext).pop();
              _poll?.cancel();
              setState(() => _phase = _Phase.form);
              context.pop();
            },
            child: const Text('Leave'),
          ),
        ],
      ),
    );
  }

  List<Widget> _body(BuildContext context) {
    switch (_phase) {
      case _Phase.form:
        return [
          Form(
            key: _formKey,
            child: TextFormField(
              controller: _phone,
              keyboardType: TextInputType.phone,
              autofillHints: const [AutofillHints.telephoneNumber],
              decoration: const InputDecoration(
                labelText: 'M-Pesa phone number',
                hintText: '0712 345 678',
                prefixIcon: Icon(Icons.phone_iphone_rounded),
              ),
              validator: validateRequiredPhone,
              onFieldSubmitted: (_) => _submit(),
            ),
          ),
          const SizedBox(height: 10),
          const Text(
            'You will receive an M-Pesa prompt on this phone. Enter your M-Pesa PIN to complete the payment.',
            style: TextStyle(color: AppColors.textSecondary, height: 1.4),
          ),
          if (_error != null) ...[
            const SizedBox(height: 14),
            Text(_error!, style: const TextStyle(color: Color(0xFFF87171))),
          ],
          const SizedBox(height: 22),
          FilledButton(
            onPressed: _submitting ? null : _submit,
            child: _submitting
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2.5, color: Colors.white),
                  )
                : Text('Pay ${formatMoney(widget.plan.price, widget.plan.currency)}'),
          ),
        ];
      case _Phase.waiting:
        return [
          const SizedBox(height: 12),
          const Center(child: Icon(Icons.phonelink_ring_rounded, size: 64, color: AppColors.success)),
          const SizedBox(height: 16),
          Text(
            'Check your phone and enter your M-Pesa PIN',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 10),
          Text(
            'We sent a payment request to ${_tx?.phoneNumber ?? 'your phone'}. '
            'This page updates automatically once M-Pesa confirms.',
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.textSecondary, height: 1.4),
          ),
          const SizedBox(height: 24),
          const Center(child: CircularProgressIndicator()),
          if (_pollWarning != null) ...[
            const SizedBox(height: 16),
            Text(
              'Still checking… ($_pollWarning)',
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.textMuted, fontSize: 12),
            ),
          ],
        ];
      case _Phase.completed:
        final tx = _tx;
        return [
          const SizedBox(height: 12),
          const Center(child: Icon(Icons.check_circle_rounded, size: 72, color: AppColors.success)),
          const SizedBox(height: 16),
          Text('Payment received',
              textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 10),
          Text(
            'Your ${tx?.planName ?? widget.plan.name} plan is active.',
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.textSecondary),
          ),
          if (tx?.externalTransactionId != null) ...[
            const SizedBox(height: 16),
            Card(
              child: ListTile(
                leading: const Icon(Icons.receipt_long_rounded),
                title: const Text('M-Pesa receipt'),
                subtitle: SelectableText(tx!.externalTransactionId!),
                trailing: Text(formatMoney(tx.amount, tx.currency)),
              ),
            ),
          ],
          const SizedBox(height: 22),
          FilledButton(
            onPressed: () => context.go('/account'),
            child: const Text('Done'),
          ),
        ];
      case _Phase.failed:
        return [
          const SizedBox(height: 12),
          const Center(child: Icon(Icons.cancel_rounded, size: 72, color: AppColors.accent)),
          const SizedBox(height: 16),
          Text('Payment not completed',
              textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 10),
          Text(
            _error ?? 'M-Pesa did not complete the payment.',
            textAlign: TextAlign.center,
            style: const TextStyle(color: AppColors.textSecondary, height: 1.4),
          ),
          const SizedBox(height: 22),
          FilledButton(onPressed: _tryAgain, child: const Text('Try again')),
        ];
      case _Phase.timedOut:
        return [
          const SizedBox(height: 12),
          const Center(child: Icon(Icons.hourglass_bottom_rounded, size: 64, color: AppColors.warning)),
          const SizedBox(height: 16),
          Text("We haven't heard from M-Pesa yet",
              textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 10),
          const Text(
            'If you completed the payment, your plan activates as soon as M-Pesa confirms it. '
            'You can check again or look at Billing history later.',
            textAlign: TextAlign.center,
            style: TextStyle(color: AppColors.textSecondary, height: 1.4),
          ),
          const SizedBox(height: 22),
          FilledButton(onPressed: _checkAgain, child: const Text('Check again')),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: _tryAgain, child: const Text('Start a new payment')),
        ];
    }
  }
}
