import { Component, Inject, Optional } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { RoomService } from '../../../core/api/room.service';

@Component({
  selector: 'app-create-room-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Create Room</h2>
    <mat-dialog-content>
      <form [formGroup]="form" id="createRoomForm" (ngSubmit)="submit()">
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Room name</mat-label>
          <input matInput formControlName="name" placeholder="e.g. general">
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Description (optional)</mat-label>
          <input matInput formControlName="description">
        </mat-form-field>
        <mat-form-field appearance="fill" style="width:100%">
          <mat-label>Visibility</mat-label>
          <mat-select formControlName="visibility">
            <mat-option value="public">Public</mat-option>
            <mat-option value="private">Private</mat-option>
          </mat-select>
        </mat-form-field>
        @if (error) { <p style="color:red;margin:0">{{ error }}</p> }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" form="createRoomForm" type="submit" [disabled]="form.invalid || loading">
        Create
      </button>
    </mat-dialog-actions>
  `,
})
export class CreateRoomDialogComponent {
  form = this.fb.nonNullable.group({
    name:        ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    description: [''],
    visibility:  ['public' as 'public' | 'private'],
  });
  error = '';
  loading = false;

  constructor(
    private fb: FormBuilder,
    private roomService: RoomService,
    private dialogRef: MatDialogRef<CreateRoomDialogComponent>,
    @Optional() @Inject(MAT_DIALOG_DATA) data?: { defaultVisibility?: 'public' | 'private' },
  ) {
    if (data?.defaultVisibility) {
      this.form.patchValue({ visibility: data.defaultVisibility });
    }
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    const { name, description, visibility } = this.form.getRawValue();
    this.roomService.create(name, description, visibility).subscribe({
      next: room => this.dialogRef.close(room),
      error: err => {
        this.error = err.status === 409 ? 'Room name already taken' : 'Could not create room';
        this.loading = false;
      },
    });
  }
}
