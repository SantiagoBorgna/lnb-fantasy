package com.fantasy.lnb.feature.premium.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsejeroResponseDto {
    private boolean isPremium;
    private List<String> advertencias;
    private List<String> consejos;
}
