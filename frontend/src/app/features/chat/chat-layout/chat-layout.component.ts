import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { WebSocketService } from '../../../core/websocket/websocket.service';
import { AfkDetectorService } from '../../../core/presence/afk-detector.service';
import { PresenceService } from '../../../core/presence/presence.service';
import { SidebarComponent } from '../sidebar/sidebar.component';
import { MembersPanelComponent } from '../members-panel/members-panel.component';
import { TopNavComponent } from '../../../shared/components/top-nav/top-nav.component';

@Component({
  selector: 'app-chat-layout',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, MembersPanelComponent, TopNavComponent],
  template: `
    <div class="app-shell">
      <app-top-nav />
      <div class="chat-layout">
        <app-sidebar />
        <main class="chat-main">
          <router-outlet />
        </main>
        <app-members-panel />
      </div>
    </div>
  `,
  styles: [`
    .app-shell { display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
    .chat-layout { display: flex; flex: 1; overflow: hidden; }
    .chat-main { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-width: 0; min-height: 0; }
  `],
})
export class ChatLayoutComponent implements OnInit, OnDestroy {
  constructor(private ws: WebSocketService, private afk: AfkDetectorService, _presence: PresenceService) {}

  ngOnInit(): void {
    this.ws.connect();
    this.afk.start();
  }

  ngOnDestroy(): void {
    this.ws.disconnect();
    this.afk.stop();
  }
}
