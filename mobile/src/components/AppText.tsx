import { Text, TextProps } from 'react-native';

import { type } from '@/theme/tokens';

type Variant = keyof typeof type;

// Text with the app's typography. Respects the system font size (UI plan 3.2): no fixed heights around text.
export function AppText({ variant = 'body', style, ...rest }: TextProps & { variant?: Variant }) {
  return <Text {...rest} style={[type[variant], style]} />;
}
