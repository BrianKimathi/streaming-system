import React, { useCallback, useEffect, useState } from 'react';
import {
  CheckCircle2,
  Eraser,
  Eye,
  EyeOff,
  FlaskConical,
  Info,
  KeyRound,
  Loader2,
  PlugZap,
  RefreshCw,
  Save,
  ShieldCheck,
  Wallet,
  XCircle,
} from 'lucide-react';
import { adminService, audit } from '../services/adminService';
import { errorMessage } from '../api/client';
import type {
  MpesaEnvironment,
  MpesaSettingField,
  MpesaSettings,
  MpesaSettingsUpdate,
  MpesaTestResult,
  MpesaTransactionType,
  SettingSource,
} from '../types';
import { ErrorBanner, LoadingState, SuccessBanner } from '../components/common/Feedback';
import { formatDateTime } from '../utils/format';
import { INPUT_CLASS, LABEL_CLASS } from './CreateMoviePage';

/** Safaricom's public Daraja sandbox test PayBill and its published Lipa Na M-Pesa Online passkey. */
const SANDBOX_SHORTCODE = '174379';
const SANDBOX_PASSKEY = 'bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919';

const BUTTON_SECONDARY =
  'flex items-center gap-2 px-3.5 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-200 rounded-lg text-xs font-semibold transition disabled:opacity-50';
const BUTTON_DANGER =
  'flex items-center gap-2 px-3.5 py-2 border border-rose-500/30 text-rose-300 hover:bg-rose-500/10 rounded-lg text-xs font-semibold transition disabled:opacity-50';

interface FormState {
  environment: MpesaEnvironment;
  shortcode: string;
  transactionType: MpesaTransactionType;
  callbackBaseUrl: string;
  consumerKey: string;
  consumerSecret: string;
  passkey: string;
}

function formFrom(settings: MpesaSettings | null): FormState {
  return {
    environment: settings?.environment ?? 'sandbox',
    shortcode: settings?.shortcode ?? '',
    transactionType: settings?.transactionType ?? 'CustomerPayBillOnline',
    callbackBaseUrl: settings?.callbackBaseUrl ?? '',
    consumerKey: '',
    consumerSecret: '',
    passkey: '',
  };
}

function validate(form: FormState): string | null {
  if (!/^\d{5,8}$/.test(form.shortcode.trim())) return 'Shortcode must be 5–8 digits.';
  const url = form.callbackBaseUrl.trim();
  try {
    if (new URL(url).protocol !== 'https:') return 'Callback base URL must start with https://.';
  } catch {
    return 'Callback base URL must be a valid https:// URL.';
  }
  return null;
}

const SOURCE_STYLE: Record<SettingSource, { label: string; className: string }> = {
  DATABASE: { label: 'Database', className: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' },
  ENVIRONMENT: { label: 'Environment', className: 'bg-sky-500/10 text-sky-400 border-sky-500/20' },
  NONE: { label: 'Not set', className: 'bg-slate-800 text-slate-400 border-slate-700' },
};

const SourceBadge: React.FC<{ source: SettingSource | undefined }> = ({ source }) => {
  const style = SOURCE_STYLE[source ?? 'NONE'] ?? SOURCE_STYLE.NONE;
  return (
    <span className={`px-2 py-0.5 rounded-full border text-[10px] font-semibold whitespace-nowrap ${style.className}`}>{style.label}</span>
  );
};

const FieldLabel: React.FC<{ label: string; source: SettingSource | undefined }> = ({ label, source }) => (
  <div className="flex items-center justify-between gap-2 mb-1">
    <label className={`${LABEL_CLASS} mb-0`}>{label}</label>
    <SourceBadge source={source} />
  </div>
);

const SecretInput: React.FC<{
  label: string;
  value: string;
  onChange: (value: string) => void;
  isSet: boolean;
  hint?: string | null;
  source: SettingSource | undefined;
  disabled?: boolean;
}> = ({ label, value, onChange, isSet, hint, source, disabled }) => {
  const [visible, setVisible] = useState(false);
  return (
    <div>
      <FieldLabel label={label} source={source} />
      <div className="relative">
        <input
          type={visible ? 'text' : 'password'}
          value={value}
          disabled={disabled}
          autoComplete="new-password"
          spellCheck={false}
          placeholder={isSet ? `${hint ?? '••••'} set — leave blank to keep` : 'Not set'}
          onChange={(e) => onChange(e.target.value)}
          className={`${INPUT_CLASS} pr-9 font-mono`}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          title={visible ? 'Hide' : 'Show what you typed'}
          className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-slate-500 hover:text-white"
        >
          {visible ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
        </button>
      </div>
    </div>
  );
};

export const PaymentsSettingsPage: React.FC = () => {
  const [settings, setSettings] = useState<MpesaSettings | null>(null);
  const [form, setForm] = useState<FormState>(() => formFrom(null));
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState<'save' | 'regenerate' | 'test' | 'clear' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<MpesaTestResult | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await adminService.getMpesaSettings();
      setSettings(data);
      setForm(formFrom(data));
    } catch (err) {
      setLoadError(errorMessage(err, 'Failed to load M-Pesa settings'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const patch = (changes: Partial<FormState>) => {
    setSuccess(null);
    setForm((f) => ({ ...f, ...changes }));
  };

  const source = (field: MpesaSettingField) => settings?.sources?.[field];
  const savedForm = formFrom(settings);
  const dirty =
    form.environment !== savedForm.environment ||
    form.shortcode.trim() !== savedForm.shortcode ||
    form.transactionType !== savedForm.transactionType ||
    form.callbackBaseUrl.trim() !== savedForm.callbackBaseUrl ||
    !!form.consumerKey.trim() ||
    !!form.consumerSecret.trim() ||
    !!form.passkey.trim();

  const save = async (regenerateCallbackToken: boolean) => {
    const invalid = validate(form);
    if (invalid) {
      setError(invalid);
      return;
    }
    const secretsUpdated = (
      [
        ['consumerKey', form.consumerKey],
        ['consumerSecret', form.consumerSecret],
        ['passkey', form.passkey],
      ] as const
    )
      .filter(([, value]) => value.trim())
      .map(([name]) => name);
    const body: MpesaSettingsUpdate = {
      environment: form.environment,
      shortcode: form.shortcode.trim(),
      transactionType: form.transactionType,
      callbackBaseUrl: form.callbackBaseUrl.trim(),
      consumerKey: form.consumerKey.trim() || null,
      consumerSecret: form.consumerSecret.trim() || null,
      passkey: form.passkey.trim() || null,
      regenerateCallbackToken,
    };

    setBusy(regenerateCallbackToken ? 'regenerate' : 'save');
    setError(null);
    setSuccess(null);
    setTestResult(null);
    try {
      const updated = await adminService.updateMpesaSettings(body);
      setSettings(updated);
      setForm(formFrom(updated));
      audit({
        action: 'MPESA_SETTINGS_UPDATED',
        targetType: 'BILLING_SETTINGS',
        targetId: 'mpesa',
        details:
          `environment=${body.environment}, shortcode=${body.shortcode}, transactionType=${body.transactionType}, ` +
          `callbackBaseUrl=${body.callbackBaseUrl}; secrets updated: ${secretsUpdated.length ? secretsUpdated.join(', ') : 'none'}` +
          (regenerateCallbackToken ? '; callback secret regenerated' : ''),
      });
      setSuccess(
        regenerateCallbackToken
          ? 'Settings saved and a new callback secret was generated.'
          : `M-Pesa settings saved${updated.configured ? '. Checkout is enabled.' : ', but the configuration is still incomplete.'}`
      );
    } catch (err) {
      setError(errorMessage(err, 'Failed to save M-Pesa settings'));
    } finally {
      setBusy(null);
    }
  };

  const regenerate = () => {
    if (
      !window.confirm(
        'Generate a new callback secret and save the form?\n\nPayment confirmations for STK pushes started before this change will be rejected, so do this when no payments are in progress.'
      )
    ) {
      return;
    }
    void save(true);
  };

  const testConnection = async () => {
    setBusy('test');
    setError(null);
    setTestResult(null);
    try {
      setTestResult(await adminService.testMpesaSettings());
    } catch (err) {
      setTestResult({ ok: false, message: errorMessage(err, 'Connection test failed') });
    } finally {
      setBusy(null);
    }
  };

  const clearSecrets = async () => {
    if (
      !window.confirm(
        'Clear the consumer key, consumer secret and passkey stored in the database?\n\nThe server falls back to its environment variables; if those are empty, M-Pesa checkout stops working.'
      )
    ) {
      return;
    }
    setBusy('clear');
    setError(null);
    setSuccess(null);
    setTestResult(null);
    try {
      await adminService.clearMpesaSecrets();
      audit({
        action: 'MPESA_SETTINGS_UPDATED',
        targetType: 'BILLING_SETTINGS',
        targetId: 'mpesa',
        details: 'Cleared stored consumer key, consumer secret and passkey',
      });
      const data = await adminService.getMpesaSettings();
      setSettings(data);
      setForm(formFrom(data));
      setSuccess('Stored secrets cleared.');
    } catch (err) {
      setError(errorMessage(err, 'Failed to clear stored secrets'));
    } finally {
      setBusy(null);
    }
  };

  const useSandboxDefaults = () => {
    patch({
      environment: 'sandbox',
      shortcode: SANDBOX_SHORTCODE,
      transactionType: 'CustomerPayBillOnline',
      passkey: SANDBOX_PASSKEY,
    });
  };

  if (loading && !settings) {
    return (
      <div className="max-w-4xl mx-auto bg-slate-900 border border-slate-800 rounded-xl">
        <LoadingState label="Loading M-Pesa settings…" />
      </div>
    );
  }

  if (!settings) {
    return (
      <div className="max-w-4xl mx-auto space-y-4">
        <ErrorBanner message={loadError ?? 'M-Pesa settings are unavailable.'} onRetry={load} />
      </div>
    );
  }

  const storedSecrets = [source('consumerKey'), source('consumerSecret'), source('passkey')].some((s) => s === 'DATABASE');
  const disabled = busy !== null;

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white flex items-center gap-2">
            <Wallet className="w-5 h-5 text-red-500" />
            Payments (M-Pesa Daraja)
          </h3>
          <p className="text-xs text-slate-400">
            Credentials for Lipa Na M-Pesa Online (STK push). Changes apply immediately; no redeploy needed. Secrets are encrypted on the
            server and never shown again.
          </p>
        </div>
        <button onClick={load} disabled={loading || disabled} className={BUTTON_SECONDARY}>
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          Reload
        </button>
      </div>

      <div
        className={`rounded-xl border p-4 flex items-start gap-3 ${
          settings.configured ? 'bg-emerald-500/10 border-emerald-500/20' : 'bg-amber-500/10 border-amber-500/20'
        }`}
      >
        {settings.configured ? (
          <ShieldCheck className="w-5 h-5 text-emerald-400 shrink-0" />
        ) : (
          <Info className="w-5 h-5 text-amber-400 shrink-0" />
        )}
        <div className="text-xs">
          <p className={`font-semibold ${settings.configured ? 'text-emerald-300' : 'text-amber-300'}`}>
            {settings.configured ? 'M-Pesa checkout is configured' : 'M-Pesa checkout is not configured'}
          </p>
          <p className="text-slate-400 mt-0.5">
            {settings.configured
              ? `Environment: ${settings.environment ?? 'unknown'}. Use "Test connection" to verify the credentials with Safaricom.`
              : 'Subscribers see "M-Pesa payments are not configured yet" until the consumer key, consumer secret, passkey, shortcode and callback URL are all set.'}
          </p>
          <p className="text-slate-500 mt-1">
            Last updated {formatDateTime(settings.updatedAt)}
            {settings.updatedBy ? ` by ${settings.updatedBy}` : ''}
          </p>
        </div>
      </div>

      <ErrorBanner message={loadError} onRetry={load} />
      <ErrorBanner message={error} />
      <SuccessBanner message={success} />

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void save(false);
        }}
        className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-6"
        autoComplete="off"
      >
        <fieldset disabled={disabled} className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Business</h4>
            <button type="button" onClick={useSandboxDefaults} className={BUTTON_SECONDARY}>
              <FlaskConical className="w-3.5 h-3.5" />
              Use Safaricom sandbox test shortcode
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <FieldLabel label="Environment" source={source('environment')} />
              <select
                value={form.environment}
                onChange={(e) => patch({ environment: e.target.value as MpesaEnvironment })}
                className={INPUT_CLASS}
              >
                <option value="sandbox">Sandbox (testing)</option>
                <option value="production">Production (live money)</option>
              </select>
            </div>
            <div>
              <FieldLabel label="Transaction type" source={source('transactionType')} />
              <select
                value={form.transactionType}
                onChange={(e) => patch({ transactionType: e.target.value as MpesaTransactionType })}
                className={INPUT_CLASS}
              >
                <option value="CustomerPayBillOnline">PayBill (CustomerPayBillOnline)</option>
                <option value="CustomerBuyGoodsOnline">Till / Buy Goods (CustomerBuyGoodsOnline)</option>
              </select>
            </div>
            <div>
              <FieldLabel label="Shortcode (PayBill / Till number)" source={source('shortcode')} />
              <input
                type="text"
                inputMode="numeric"
                maxLength={8}
                value={form.shortcode}
                onChange={(e) => patch({ shortcode: e.target.value.replace(/\D/g, '') })}
                placeholder="e.g. 174379"
                className={INPUT_CLASS}
              />
              {form.environment === 'production' && form.shortcode === SANDBOX_SHORTCODE && (
                <p className="text-[11px] text-amber-300 mt-1">174379 is the sandbox test shortcode; production needs your own.</p>
              )}
            </div>
            <div>
              <FieldLabel label="Callback base URL" source={source('callbackBaseUrl')} />
              <input
                type="url"
                value={form.callbackBaseUrl}
                onChange={(e) => patch({ callbackBaseUrl: e.target.value })}
                placeholder="https://streamxapi.briankimathi.dev"
                className={INPUT_CLASS}
              />
              <p className="text-[10px] text-slate-500 mt-1">Public https address of the API that Safaricom calls with payment results.</p>
            </div>
          </div>
        </fieldset>

        <fieldset disabled={disabled} className="space-y-4 border-t border-slate-800 pt-4">
          <h4 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-2">
            <KeyRound className="w-4 h-4 text-amber-400" />
            Daraja credentials
          </h4>
          <p className="text-[11px] text-slate-500">
            Consumer key and secret come from your own app on the Safaricom Daraja portal (developer.safaricom.co.ke → My Apps). Leave a
            field blank to keep the stored value.
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <SecretInput
              label="Consumer key"
              value={form.consumerKey}
              onChange={(consumerKey) => patch({ consumerKey })}
              isSet={settings.consumerKeySet}
              hint={settings.consumerKeyHint}
              source={source('consumerKey')}
            />
            <SecretInput
              label="Consumer secret"
              value={form.consumerSecret}
              onChange={(consumerSecret) => patch({ consumerSecret })}
              isSet={settings.consumerSecretSet}
              source={source('consumerSecret')}
            />
            <div className="sm:col-span-2">
              <SecretInput
                label="Lipa Na M-Pesa Online passkey"
                value={form.passkey}
                onChange={(passkey) => patch({ passkey })}
                isSet={settings.passkeySet}
                source={source('passkey')}
              />
            </div>
          </div>

          <div className="bg-slate-950 border border-slate-800 rounded-lg p-4 flex flex-wrap items-center justify-between gap-3">
            <div className="text-xs">
              <p className="font-semibold text-slate-300 flex items-center gap-2">
                Callback secret
                <SourceBadge source={source('callbackToken')} />
              </p>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {settings.callbackTokenSet
                  ? 'Set. It is embedded in the callback URL so only Safaricom can confirm payments.'
                  : 'Not set yet: one is generated automatically when you save.'}
              </p>
            </div>
            <button type="button" onClick={regenerate} className={BUTTON_SECONDARY}>
              {busy === 'regenerate' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
              Regenerate callback secret
            </button>
          </div>
        </fieldset>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-800 pt-4">
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={() => void testConnection()} disabled={disabled} className={BUTTON_SECONDARY}>
              {busy === 'test' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <PlugZap className="w-3.5 h-3.5" />}
              Test connection
            </button>
            <button
              type="button"
              onClick={() => void clearSecrets()}
              disabled={disabled || !storedSecrets}
              title={storedSecrets ? undefined : 'No secrets are stored in the database'}
              className={BUTTON_DANGER}
            >
              {busy === 'clear' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Eraser className="w-3.5 h-3.5" />}
              Clear stored secrets
            </button>
          </div>
          <button
            type="submit"
            disabled={disabled || !dirty}
            className="flex items-center gap-2 px-5 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-semibold transition disabled:opacity-50"
          >
            {busy === 'save' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
            Save settings
          </button>
        </div>

        {dirty && busy !== 'test' && (
          <p className="text-[11px] text-slate-500">"Test connection" uses the saved settings, so save your changes first.</p>
        )}
        {testResult && (
          <div
            className={`p-3 rounded-lg border text-xs font-medium flex items-start gap-2 ${
              testResult.ok
                ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-300'
                : 'bg-rose-500/10 border-rose-500/20 text-rose-300'
            }`}
          >
            {testResult.ok ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <XCircle className="w-4 h-4 shrink-0" />}
            <span className="break-words">{testResult.message}</span>
          </div>
        )}
      </form>
    </div>
  );
};
