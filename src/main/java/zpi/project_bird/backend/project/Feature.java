package zpi.project_bird.backend.project;

import java.math.BigDecimal;
import java.util.UUID;

public record Feature(
    String id,
    String colorSpace,
    BigDecimal pixelSize,
    String sourceUrl,
    String objectName,
    Integer fileSize
) {
}
