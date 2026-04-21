import { Component, Input } from '@angular/core';
import { NgClass } from '@angular/common';

@Component({
  selector: 'app-presence-badge',
  standalone: true,
  imports: [NgClass],
  template: `<span class="badge" [ngClass]="status" [title]="status"></span>`,
  styles: [`
    .badge { display: inline-block; width: 10px; height: 10px; border-radius: 50%; }
    .online  { background: #43b581; }
    .afk     { background: #faa61a; }
    .offline { background: #747f8d; }
  `],
})
export class PresenceBadgeComponent {
  @Input() status: 'online' | 'afk' | 'offline' = 'offline';
}
