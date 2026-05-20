package com.echcherqaoui.jobboard.userservice.dto.response;


import com.echcherqaoui.jobboard.userservice.enums.SkillLevel;

import java.util.UUID;

public record SkillResponse(
      UUID id,
      String skillName,
      SkillLevel level) {
}