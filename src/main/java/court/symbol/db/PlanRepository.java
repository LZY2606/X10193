package court.symbol.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import court.symbol.model.PlatformPolicy;
import court.symbol.model.ResolutionReport;
import court.symbol.service.Dtos;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Repository
public class PlanRepository {

    private final SchemaInitializer db;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanRepository(SchemaInitializer db) {
        this.db = db;
    }

    public void insertPlan(String id, String name, String targetPlatform, long now) {
        try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO plan(id,name,target_platform,created_at) VALUES(?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, targetPlatform);
            ps.setLong(4, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public int nextVersion(String planId) {
        try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(version),0)+1 FROM plan_version WHERE plan_id=?")) {
            ps.setString(1, planId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 1;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void insertVersion(String planId, int version, PlatformPolicy policy,
                              Object spec, ResolutionReport report) {
        try {
            String specJson = mapper.writeValueAsString(spec);
            String reportJson = mapper.writeValueAsString(report);
            try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO plan_version(plan_id,version,link_model,tie_policy,"
                            + "allow_interposition,spec_json,report_json,created_at)"
                            + " VALUES(?,?,?,?,?,?,?,?)")) {
                ps.setString(1, planId);
                ps.setInt(2, version);
                ps.setString(3, policy.linkModel());
                ps.setString(4, policy.tiePolicy());
                ps.setInt(5, policy.allowInterposition() ? 1 : 0);
                ps.setString(6, specJson);
                ps.setString(7, reportJson);
                ps.setLong(8, report.createdAt());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ResolutionReport loadReport(String planId, int version) {
        try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT report_json FROM plan_version WHERE plan_id=? AND version=?")) {
            ps.setString(1, planId);
            ps.setInt(2, version);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return mapper.readValue(rs.getString(1), ResolutionReport.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Dtos.CreatePlanRequest loadSpec(String planId, int version) {
        try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT spec_json FROM plan_version WHERE plan_id=? AND version=?")) {
            ps.setString(1, planId);
            ps.setInt(2, version);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return mapper.readValue(rs.getString(1), Dtos.CreatePlanRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Dtos.PlanSummary> listPlans() {
        try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT p.id,p.name,p.target_platform,p.created_at,"
                        + "COALESCE(MAX(v.version),0) FROM plan p LEFT JOIN plan_version v"
                        + " ON v.plan_id=p.id GROUP BY p.id ORDER BY p.created_at DESC")) {
            ResultSet rs = ps.executeQuery();
            List<Dtos.PlanSummary> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Dtos.PlanSummary(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getInt(5), rs.getLong(4)));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<Dtos.VersionSummary> listVersions(String planId) {
        try (var c = db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT version,link_model,tie_policy,allow_interposition,created_at,report_json"
                        + " FROM plan_version WHERE plan_id=? ORDER BY version")) {
            ps.setString(1, planId);
            ResultSet rs = ps.executeQuery();
            List<Dtos.VersionSummary> out = new ArrayList<>();
            while (rs.next()) {
                ResolutionReport r = mapper.readValue(rs.getString(6), ResolutionReport.class);
                out.add(new Dtos.VersionSummary(planId, rs.getInt(1), rs.getString(2),
                        rs.getString(3), rs.getInt(4) == 1, rs.getLong(5),
                        r.decisions().size(), r.unresolved().size(), r.ambiguous().size()));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
