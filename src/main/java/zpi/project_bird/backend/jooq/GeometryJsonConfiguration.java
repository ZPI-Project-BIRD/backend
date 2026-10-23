package zpi.project_bird.backend.jooq;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Configuration(proxyBeanMethods = false)
public class GeometryJsonConfiguration {
    @Bean
    JsonMapperBuilderCustomizer geometryJsonCustomizer() {
        return builder -> {
            var module = new SimpleModule("jts-geojson");
            module.addSerializer(Geometry.class, new GeometrySerializer());
            builder.addModule(module);
        };
    }

    private static final class GeometrySerializer extends ValueSerializer<Geometry> {
        @Override
        public void serialize(
                Geometry geometry,
                JsonGenerator generator,
                SerializationContext context) throws JacksonException {
            generator.writeRawValue(new GeoJsonWriter().write(geometry));
        }
    }
}
