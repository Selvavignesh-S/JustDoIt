import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { Task } from '@/api/types';
import { Button } from '@/components/Button';
import { DeadlineField } from '@/components/DeadlineField';
import { Sheet } from '@/components/Sheet';
import { strings } from '@/strings/en';
import { space } from '@/theme/tokens';

type Props = { task: Task | null; saving: boolean; onSave: (deadline: Date) => void; onClose: () => void };

// Reschedule (UI plan 7.14): only the deadline changes; the server recalculates current priority in the same request
export function RescheduleSheet({ task, saving, onSave, onClose }: Props) {
  // Re-mounted per task (key in the parent), so this starts from that task's deadline
  const [deadline, setDeadline] = useState<Date | null>(task ? new Date(task.deadline) : null);
  const [error, setError] = useState<string | undefined>();

  const save = () => {
    if (!deadline || deadline.getTime() <= Date.now()) {
      setError(strings.deadline.future);
      return;
    }
    onSave(deadline);
  };

  return (
    <Sheet visible={!!task} onClose={onClose} title={strings.tasks.reschedule} subtitle={task?.title}>
      <DeadlineField
        label={strings.tasks.newDeadline}
        value={deadline}
        onChange={(d) => {
          setDeadline(d);
          setError(undefined);
        }}
        error={error}
        hint={strings.tasks.rescheduleHint}
      />
      <View style={styles.actions}>
        <Button label={strings.tasks.save} onPress={save} loading={saving} />
        <Button variant="secondary" label={strings.tasks.cancel} onPress={onClose} disabled={saving} />
      </View>
    </Sheet>
  );
}

const styles = StyleSheet.create({
  actions: { gap: space.s, marginTop: space.s },
});
