package zpi.project_bird.backend.jooq.binding;

import java.sql.SQLException;
import java.sql.Types;

import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.exception.DataTypeException;
import org.jooq.impl.AbstractBinding;
import org.jooq.impl.DSL;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

public final class PostgisGeometryBinding extends AbstractBinding<String, Geometry> {
    private static final long serialVersionUID = 1L;

    private static final Converter<String, Geometry> CONVERTER = Converter.ofNullable(
        String.class,
        Geometry.class,
        PostgisGeometryBinding::read,
        PostgisGeometryBinding::write);

    @Override
    public Converter<String, Geometry> converter() {
        return CONVERTER;
    }

    @Override
    protected void sqlInline(BindingSQLContext<Geometry> context) {
        context.render()
            .visit(DSL.inline(write(context.value())))
            .sql("::geometry");
    }

    @Override
    protected void sqlBind(BindingSQLContext<Geometry> context) {
        context.render().sql(context.variable()).sql("::geometry");
    }

    @Override
    public void set(BindingSetStatementContext<Geometry> context) throws SQLException {
        context.statement().setString(
            context.index(),
            write(context.value()));
    }

    @Override
    public void get(BindingGetResultSetContext<Geometry> context) throws SQLException {
        context.value(read(context.resultSet().getString(context.index())));
    }

    @Override
    public void register(BindingRegisterContext<Geometry> context) throws SQLException {
        context.statement().registerOutParameter(context.index(), Types.OTHER);
    }

    @Override
    public void get(BindingGetStatementContext<Geometry> context) throws SQLException {
        context.value(read(context.statement().getString(context.index())));
    }

    @Override
    public void set(BindingSetSQLOutputContext<Geometry> context) throws SQLException {
        context.output().writeString(write(context.value()));
    }

    @Override
    public void get(BindingGetSQLInputContext<Geometry> context) throws SQLException {
        context.value(read(context.input().readString()));
    }

    private static Geometry read(String value) {
        if (value == null) {
            return null;
        }

        try {
            return new WKBReader().read(WKBReader.hexToBytes(value));
        } catch (ParseException exception) {
            throw new DataTypeException("Invalid PostGIS geometry", exception);
        }
    }

    private static String write(Geometry value) {
        return value == null ? null : WKBWriter.toHex(new WKBWriter(2, true).write(value));
    }
}
