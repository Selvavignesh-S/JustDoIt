import { AppText } from '@/components/AppText';
import { Screen } from '@/components/Screen';
import { strings } from '@/strings/en';

// S11 Completed. shortcut: placeholder until Phase 3 builds the completed list.
export default function CompletedScreen() {
  return (
    <Screen edges={['top']}>
      <AppText variant="heading">{strings.completed.title}</AppText>
      <AppText variant="caption">{strings.completed.empty}</AppText>
    </Screen>
  );
}
