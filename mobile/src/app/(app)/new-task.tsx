import { router } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet, TextInput, View } from 'react-native';

import { isApiError } from '@/api/errors';
import { analyzeDescription } from '@/api/tasks';
import { Suggestion } from '@/api/types';
import { AppText } from '@/components/AppText';
import { Banner } from '@/components/Banner';
import { Button } from '@/components/Button';
import { DeadlineField } from '@/components/DeadlineField';
import { Screen } from '@/components/Screen';
import { strings } from '@/strings/en';
import { recallAnalysis, rememberAnalysis } from '@/tasks/analyzeMemory';
import { colors, fonts, radius, space } from '@/theme/tokens';
import { normalizeDescription } from '@/utils/format';

const MAX = 500;
type Problem = 'rate' | 'unreachable' | null;

// S4 Describe (UI plan 7.6, 9). Only the description goes to the AI; the deadline stays on the phone.
export default function NewTaskScreen() {
  const [text, setText] = useState('');
  const [deadline, setDeadline] = useState<Date | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [stillWorking, setStillWorking] = useState(false);
  const [problem, setProblem] = useState<Problem>(null);
  // Checked when a date is picked and again on Analyze (time may pass while the screen is open)
  const [deadlineInPast, setDeadlineInPast] = useState(false);
  const abort = useRef<AbortController | null>(null);

  const normalized = normalizeDescription(text);
  const canAnalyze = normalized.length > 0 && deadline !== null && !deadlineInPast && !analyzing;

  const pickDeadline = (d: Date) => {
    setDeadline(d);
    setDeadlineInPast(d.getTime() <= Date.now());
  };

  const openForm = (params: Record<string, string>) =>
    router.push({
      pathname: '/task-form',
      params: { deadline: deadline?.toISOString() ?? '', description: text.trim(), ...params },
    });
  const openReview = (s: Suggestion) =>
    openForm({ mode: 'review', title: s.title, category: s.category, hours: String(s.estimatedHours) });
  const openManual = (aiOff: boolean) => openForm({ mode: 'manual', aiOff: aiOff ? '1' : '' });

  const analyze = async () => {
    setProblem(null);
    if (!deadline || deadline.getTime() <= Date.now()) {
      setDeadlineInPast(true);
      return;
    }
    // Same text as the last successful analysis: reuse it, no call (SRS 8.6 rule 9)
    const cached = recallAnalysis(normalized);
    if (cached) return openReview(cached);

    abort.current = new AbortController();
    setAnalyzing(true);
    const slow = setTimeout(() => setStillWorking(true), 8_000);
    try {
      const suggestion = await analyzeDescription(normalized, abort.current.signal);
      rememberAnalysis(normalized, suggestion);
      openReview(suggestion);
    } catch (e) {
      // Only the server's explicit AI_UNAVAILABLE opens the manual form; a network error or a waking
      // server is not an AI failure and stays here with Retry (UI plan 7.6 step 5-7). No auto-retry.
      if (isApiError(e, 'AI_UNAVAILABLE')) openManual(true);
      else if (isApiError(e, 'RATE_LIMITED')) setProblem('rate');
      else if (!isApiError(e, 'CANCELLED')) setProblem('unreachable');
    } finally {
      clearTimeout(slow);
      setStillWorking(false);
      setAnalyzing(false);
    }
  };

  return (
    <Screen edges={['bottom']}>
      <View style={styles.field}>
        <AppText variant="label">{strings.create.describeLabel}</AppText>
        <TextInput
          value={text}
          onChangeText={setText}
          maxLength={MAX}
          multiline
          editable={!analyzing}
          accessibilityLabel={strings.create.describeLabel}
          accessibilityHint={strings.create.describeHint}
          style={styles.describe}
          textAlignVertical="top"
        />
        <View style={styles.hintRow}>
          <AppText variant="caption" style={styles.hint}>
            {strings.create.describeHint}
          </AppText>
          {/* Live counter: amber from 450, cap note at 500 (UI plan 7.6) */}
          <AppText variant="caption" style={text.length >= 450 ? { color: colors.medium } : undefined}>
            {text.length}/{MAX}
          </AppText>
        </View>
        {text.length >= MAX ? <AppText variant="caption">{strings.create.capReached}</AppText> : null}
      </View>

      <DeadlineField
        label={strings.create.deadlineLabel}
        value={deadline}
        onChange={pickDeadline}
        error={deadlineInPast ? strings.deadline.future : undefined}
        hint={strings.create.deadlineHint}
      />

      <View style={styles.actions}>
        <Button label={analyzing ? strings.create.analyzing : strings.create.analyze} onPress={analyze} disabled={!canAnalyze} loading={false} />
        {analyzing ? <Button variant="link" label={strings.create.cancel} onPress={() => abort.current?.abort()} /> : null}
        {stillWorking ? <AppText variant="caption" style={styles.center}>{strings.create.stillWorking}</AppText> : null}

        {problem === 'rate' ? <Banner tone="warning" message={strings.create.rateLimited} /> : null}
        {problem === 'unreachable' ? (
          <View style={styles.actions}>
            <Banner tone="error" message={strings.create.unreachable} />
            <Button variant="secondary" label={strings.create.retry} onPress={analyze} disabled={!canAnalyze} />
          </View>
        ) : null}

        <Button variant="link" label={strings.create.manual} onPress={() => openManual(false)} disabled={analyzing} />
      </View>

      {/* Privacy line (UI decision D17) */}
      <AppText variant="caption" style={styles.center}>
        {strings.create.privacy}
      </AppText>
    </Screen>
  );
}

const styles = StyleSheet.create({
  field: { gap: space.xs },
  describe: {
    minHeight: 112,
    borderWidth: 1,
    borderColor: colors.inputBorder,
    borderRadius: radius.input,
    backgroundColor: colors.surface,
    padding: space.m,
    fontFamily: fonts.body,
    fontSize: 15,
    color: colors.ink,
  },
  hintRow: { flexDirection: 'row', justifyContent: 'space-between', gap: space.m },
  hint: { flex: 1 },
  actions: { gap: space.s },
  center: { textAlign: 'center' },
});
