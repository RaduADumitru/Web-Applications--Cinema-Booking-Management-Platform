package com.awbd.cinema.validators;

import com.awbd.cinema.DTOs.AuthDTOs.RegisterDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, RegisterDTO> {

    @Override
    public boolean isValid(RegisterDTO dto, ConstraintValidatorContext context) {
        if (dto.password() == null || dto.confirmPassword() == null) {
            return false;
        }

        boolean isValid = dto.password().equals(dto.confirmPassword());

        if (!isValid) {
            context.disableDefaultConstraintViolation();
            context
                    .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmPassword")
                    .addConstraintViolation();
        }

        return isValid;
    }
}