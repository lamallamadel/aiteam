import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-container">
      @for (line of lineArray; track $index) {
        <div
          class="skeleton-line"
          [style.width]="getWidth($index)"
          [style.height.px]="lineHeight"
        ></div>
      }
    </div>
  `,
  styles: [`
    .skeleton-container {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .skeleton-line {
      background: linear-gradient(
        90deg,
        rgba(255, 255, 255, 0.03) 25%,
        rgba(255, 255, 255, 0.08) 50%,
        rgba(255, 255, 255, 0.03) 75%
      );
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
      border-radius: 4px;
    }
    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }
  `]
})
export class SkeletonLoaderComponent {
  @Input() lines = 3;
  @Input() lineHeight = 16;

  get lineArray(): number[] {
    return Array.from({ length: this.lines }, (_, i) => i);
  }

  getWidth(index: number): string {
    const widths = ['100%', '85%', '70%', '90%', '60%', '95%', '75%'];
    return widths[index % widths.length];
  }
}
