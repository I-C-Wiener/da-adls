import { Injectable, OnDestroy } from '@angular/core';
import { fromEvent, merge, Subscription, BehaviorSubject, distinctUntilChanged } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { WebSocketService } from '../websocket/websocket.service';

const AFK_DEBOUNCE_MS = 60_000;
const PING_INTERVAL_MS = 30_000;

@Injectable({ providedIn: 'root' })
export class AfkDetectorService implements OnDestroy {
  private activeTab$ = new BehaviorSubject(true);
  private pingInterval: ReturnType<typeof setInterval> | null = null;
  private activitySub: Subscription | null = null;
  private afkSub: Subscription | null = null;
  private transitionSub: Subscription | null = null;

  constructor(private ws: WebSocketService) {}

  start(): void {
    const activity$ = merge(
      fromEvent(document, 'mousemove'),
      fromEvent(document, 'keydown'),
      fromEvent(document, 'click'),
    );

    this.activitySub = activity$.subscribe(() => {
      this.activeTab$.next(true);
    });

    // AFK: no activity for 1 minute → mark idle
    this.afkSub = activity$.pipe(debounceTime(AFK_DEBOUNCE_MS)).subscribe(() => {
      this.activeTab$.next(false);
    });

    // Whenever active/idle state changes, send an immediate ping so
    // the server propagates the new presence status within <2s.
    this.transitionSub = this.activeTab$.pipe(distinctUntilChanged()).subscribe(active => {
      this.ws.send({ type: 'ping', activeTab: active });
    });

    // Periodic heartbeat so the server knows the tab is still open.
    this.pingInterval = setInterval(() => {
      this.ws.send({ type: 'ping', activeTab: this.activeTab$.value });
    }, PING_INTERVAL_MS);
  }

  stop(): void {
    this.activitySub?.unsubscribe();
    this.afkSub?.unsubscribe();
    this.transitionSub?.unsubscribe();
    if (this.pingInterval) clearInterval(this.pingInterval);
  }

  ngOnDestroy(): void {
    this.stop();
  }
}
