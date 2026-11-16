import type * as React from 'react';

/** [x, y, level]: x/y in 0..24, level 0..1 (1 = full white). */
export type Cell = [number, number, number?];

export interface GlyphMatrixProps { cells?: Cell[]; signal?: [number, number][]; size?: number; lens?: boolean; glow?: boolean; led?: 'round' | 'square'; label?: string }
export declare function GlyphMatrix(props: GlyphMatrixProps): React.ReactElement;

export interface AppIconProps { cells?: Cell[]; signal?: [number, number][]; size?: number; fill?: number; shape?: 'squircle' | 'circle' | 'square'; label?: string }
export declare function AppIcon(props: AppIconProps): React.ReactElement;

export interface PhoneBackProps { width?: number; cells?: Cell[]; signal?: [number, number][]; tilt?: number; src?: string; alt?: string }
export declare function PhoneBack(props: PhoneBackProps): React.ReactElement;

export interface DotHeadlineProps { lines: string[]; size?: number | string; stops?: boolean; as?: string; className?: string; style?: React.CSSProperties }
export declare function DotHeadline(props: DotHeadlineProps): React.ReactElement;

export interface SignatureLabelProps { children?: React.ReactNode; style?: React.CSSProperties }
export declare function SignatureLabel(props: SignatureLabelProps): React.ReactElement;

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> { variant?: 'primary' | 'secondary'; block?: boolean }
export declare function Button(props: ButtonProps): React.ReactElement;

export interface ScoreDisplayProps { value?: number; label?: string; digits?: number; size?: number }
export declare function ScoreDisplay(props: ScoreDisplayProps): React.ReactElement;

export interface ToyCardProps { title: string; summary?: string; cells?: Cell[]; signal?: [number, number][]; matrixSize?: number; action?: React.ReactNode }
export declare function ToyCard(props: ToyCardProps): React.ReactElement;

export interface StoreShotProps { headline: string[]; sub?: string; signature?: string; width?: number; children?: React.ReactNode }
export declare function StoreShot(props: StoreShotProps): React.ReactElement;

declare global {
  interface Window {
    N7Glyph: {
      GlyphMatrix: typeof GlyphMatrix; AppIcon: typeof AppIcon; PhoneBack: typeof PhoneBack; DotHeadline: typeof DotHeadline;
      SignatureLabel: typeof SignatureLabel; Button: typeof Button; ScoreDisplay: typeof ScoreDisplay; ToyCard: typeof ToyCard; StoreShot: typeof StoreShot;
      frames: { snakeIcon: { cells: Cell[]; signal: [number, number][] }; snakePlay: { cells: Cell[] }; flappyIcon: { cells: Cell[]; signal: [number, number][] } };
      line(x0: number, y0: number, x1: number, y1: number): [number, number][];
      snake(path: [number, number][]): Cell[];
      isVisible(x: number, y: number): boolean;
      BOUNDS: [number, number][];
      PHONE: { src: string; w: number; h: number; cx: number; cy: number; r: number };
    };
  }
}
