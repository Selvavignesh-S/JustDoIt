// Design tokens from the UI plan (section 3). Light theme only in V1; semantic names so a dark theme is a token swap later.
import { TextStyle } from 'react-native';

export const colors = {
  bg: '#F6F7FB',
  surface: '#FFFFFF',
  ink: '#171E45',
  muted: '#5A6283',
  line: '#E2E5F0',
  inputBorder: '#C9CFE4',
  brand: '#3448E0',
  brandTint: '#E8EBFC',
  infoInk: '#1F2A7A',
  dueTomorrow: '#C8102E',
  dueTomorrowTint: '#FDECEF',
  high: '#C2570C',
  highTint: '#FDF0E6',
  medium: '#8A6D00',
  mediumTint: '#FBF4DC',
  low: '#0F766E',
  lowTint: '#E3F4F2',
  done: '#1E7B4A',
  doneTint: '#E3F4EA',
  neutralChipBg: '#ECEEF7',
  neutralChipText: '#3A4270',
  warnBg: '#FFF3D1',
  warnText: '#6B5200',
  checkBorder: '#AEB6D6',
  scrim: 'rgba(10,14,40,0.5)',
  white: '#FFFFFF',
} as const;

// 4-pt grid
export const space = { xs: 4, s: 8, m: 12, l: 16, xl: 24, xxl: 32 } as const;

export const radius = { card: 14, input: 12, sheet: 20, dialog: 16, pill: 999 } as const;

// Every tappable target is at least this tall/wide (UI plan 3.3)
export const minTouch = 44;

// Names registered by useFonts in the root layout
export const fonts = {
  heading: 'BricolageGrotesque_700Bold',
  headingSemi: 'BricolageGrotesque_600SemiBold',
  body: 'Figtree_400Regular',
  bodyMedium: 'Figtree_500Medium',
  bodySemi: 'Figtree_600SemiBold',
  bodyBold: 'Figtree_700Bold',
} as const;

export const type = {
  title: { fontFamily: fonts.heading, fontSize: 28, lineHeight: 32, color: colors.ink },
  heading: { fontFamily: fonts.heading, fontSize: 22, lineHeight: 28, color: colors.ink },
  section: { fontFamily: fonts.headingSemi, fontSize: 17, lineHeight: 24, color: colors.ink },
  body: { fontFamily: fonts.body, fontSize: 15, lineHeight: 22, color: colors.ink },
  bodyStrong: { fontFamily: fonts.bodySemi, fontSize: 15, lineHeight: 22, color: colors.ink },
  caption: { fontFamily: fonts.body, fontSize: 13, lineHeight: 18, color: colors.muted },
  label: { fontFamily: fonts.bodySemi, fontSize: 13, lineHeight: 18, color: colors.ink },
  chip: { fontFamily: fonts.bodySemi, fontSize: 12, lineHeight: 16 },
} satisfies Record<string, TextStyle>;
