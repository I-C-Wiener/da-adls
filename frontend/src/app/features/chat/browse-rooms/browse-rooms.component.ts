import { Component, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { Room, RoomService } from '../../../core/api/room.service';

@Component({
  selector: 'app-browse-rooms',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatCardModule, MatIconModule],
  template: `
    <div class="browse-container">
      <h2>Browse Rooms</h2>
      <mat-form-field appearance="outline" style="width:100%;max-width:400px">
        <mat-label>Search rooms</mat-label>
        <mat-icon matPrefix>search</mat-icon>
        <input matInput [formControl]="searchCtrl" placeholder="Room name...">
      </mat-form-field>

      <div class="rooms-grid">
        @for (room of rooms(); track room.id) {
          <mat-card class="room-card">
            <mat-card-header>
              <mat-card-title># {{ room.name }}</mat-card-title>
              <mat-card-subtitle>{{ room.visibility }} · {{ room.memberCount ?? 0 }} members</mat-card-subtitle>
            </mat-card-header>
            @if (room.description) {
              <mat-card-content>{{ room.description }}</mat-card-content>
            }
            <mat-card-actions>
              <button mat-button color="primary" (click)="joinAndOpen(room)">Join</button>
            </mat-card-actions>
          </mat-card>
        }
        @if (rooms().length === 0) {
          <p class="no-results">No rooms found.</p>
        }
      </div>
    </div>
  `,
  styles: [`
    .browse-container { padding: 24px; overflow-y: auto; height: 100%; }
    .rooms-grid { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 16px; }
    .room-card { width: 240px; }
    .no-results { color: #888; }
  `],
})
export class BrowseRoomsComponent implements OnInit {
  rooms = signal<Room[]>([]);
  searchCtrl = new FormControl('');

  constructor(private roomService: RoomService, private router: Router) {}

  ngOnInit(): void {
    this.searchCtrl.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => this.roomService.list(q ?? undefined)),
    ).subscribe(rooms => this.rooms.set(rooms));

    this.roomService.list().subscribe(rooms => this.rooms.set(rooms));
  }

  joinAndOpen(room: Room): void {
    this.roomService.join(room.id).subscribe({
      next: () => this.router.navigate(['/chat/room', room.id]),
      error: () => this.router.navigate(['/chat/room', room.id]),
    });
  }
}
