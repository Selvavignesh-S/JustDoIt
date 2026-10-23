import { useState } from 'react';
import { Pressable, StyleSheet, TextInput, TextInputProps, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { strings } from '@/strings/en';
import { colors, fonts, minTouch, radius, space } from '@/theme/tokens';

type Props = TextInputProps & {
  label: string;
  hint?: string;
  error?: string;
  // Password fields get a Show/Hide toggle (UI plan 7.1)
  secret?: boolean;
};

export function TextField({ label, hint, error, secret, style, ...input }: Props) {
  const [hidden, setHidden] = useState(true);
  return (
    <View style={styles.wrap}>
      <AppText variant="label">{label}</AppText>
      <View style={[styles.box, error ? styles.boxError : null]}>
        <TextInput
          {...input}
          secureTextEntry={secret ? hidden : input.secureTextEntry}
          placeholderTextColor={colors.muted}
          accessibilityLabel={label}
          accessibilityHint={error ?? hint}
          style={[styles.input, style]}
        />
        {secret ? (
          <Pressable
            onPress={() => setHidden((h) => !h)}
            accessibilityRole="button"
            accessibilityLabel={hidden ? `${strings.common.show} ${label}` : `${strings.common.hide} ${label}`}
            hitSlop={8}
            style={styles.toggle}>
            <AppText style={styles.toggleText}>{hidden ? strings.common.show : strings.common.hide}</AppText>
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <AppText variant="caption" style={styles.error} accessibilityLiveRegion="polite">
          {error}
        </AppText>
      ) : hint ? (
        <AppText variant="caption">{hint}</AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: space.xs },
  box: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: minTouch + 4,
    borderWidth: 1,
    borderColor: colors.inputBorder,
    borderRadius: radius.input,
    backgroundColor: colors.surface,
  },
  boxError: { borderColor: colors.dueTomorrow },
  input: { flex: 1, paddingHorizontal: space.m, paddingVertical: space.s, fontFamily: fonts.body, fontSize: 15, color: colors.ink },
  toggle: { minHeight: minTouch, justifyContent: 'center', paddingHorizontal: space.m },
  toggleText: { fontFamily: fonts.bodySemi, color: colors.brand, fontSize: 13 },
  error: { color: colors.dueTomorrow },
});
