import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
  type ValidatorFn,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly showPassword = signal(false);
  readonly loading = signal(false);
  readonly error = signal('');

  readonly pwdPattern = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;

  readonly form = this.fb.nonNullable.group(
    {
      full_name: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(100)]],
      document: ['', [Validators.required, Validators.minLength(8)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6), Validators.pattern(this.pwdPattern)]],
      confirmPassword: ['', [Validators.required]],
    },
    { validators: matchPasswords },
  );

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  fieldInvalid(fieldName: 'full_name' | 'document' | 'email' | 'password' | 'confirmPassword'): boolean {
    const field = this.form.get(fieldName);
    return !!field && field.invalid && (field.touched || field.dirty);
  }

  fieldMessage(fieldName: 'full_name' | 'document' | 'email' | 'password' | 'confirmPassword'): string {
    const field = this.form.get(fieldName);
    if (!field?.errors) return '';

    if (field.errors['required']) return 'Este campo es obligatorio.';
    if (field.errors['email']) return 'Ingrese un correo válido.';
    if (field.errors['minlength']) {
      if (fieldName === 'full_name') return 'El nombre debe tener al menos 10 caracteres.';
      if (fieldName === 'document') return 'El documento debe tener al menos 8 caracteres.';
      if (fieldName === 'password') return 'La contraseña debe tener al menos 6 caracteres.';
    }
    if (field.errors['maxlength'] && fieldName === 'full_name') return 'El nombre no puede superar 100 caracteres.';
    if (field.errors['pattern'] && fieldName === 'password') {
      return 'La contraseña debe tener mayúscula, minúscula y número.';
    }
    return 'Revise este campo.';
  }

  private formValidationMessage(): string {
    if (this.form.errors?.['mismatch']) return 'Las contraseñas no coinciden';
    if (this.form.get('full_name')?.invalid) return this.fieldMessage('full_name');
    if (this.form.get('document')?.invalid) return this.fieldMessage('document');
    if (this.form.get('email')?.invalid) return this.fieldMessage('email');
    if (this.form.get('password')?.invalid) return this.fieldMessage('password');
    if (this.form.get('confirmPassword')?.invalid) return this.fieldMessage('confirmPassword');
    return 'Revise los datos del formulario.';
  }

  submit(): void {
    this.error.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set(this.formValidationMessage());
      return;
    }
    const v = this.form.getRawValue();
    this.loading.set(true);
    this.auth
      .registerAndLogin({
        name: v.full_name,
        document: v.document,
        email: v.email,
        password: v.password,
      })
      .subscribe((res) => {
        this.loading.set(false);
        if (res.ok) void this.router.navigate(['/dashboard']);
        else this.error.set(res.error);
      });
  }
}

const matchPasswords: ValidatorFn = (group: AbstractControl): ValidationErrors | null => {
  const p = group.get('password')?.value;
  const c = group.get('confirmPassword')?.value;
  return p === c ? null : { mismatch: true };
};
