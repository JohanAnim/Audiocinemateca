import { IsNotEmpty, IsString, MinLength } from 'class-validator';

export class LoginDto {
  @IsString({ message: 'El identificador (correo o usuario) es requerido' })
  @IsNotEmpty({ message: 'El identificador no puede estar vacío' })
  identifier!: string;

  @IsString({ message: 'La contraseña es requerida' })
  @IsNotEmpty({ message: 'La contraseña no puede estar vacía' })
  @MinLength(6, { message: 'La contraseña debe tener al menos 6 caracteres' })
  password!: string;
}
