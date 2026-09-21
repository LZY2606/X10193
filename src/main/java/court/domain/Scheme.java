package court.domain;

import court.link.LinkPolicy;

import java.util.ArrayList;
import java.util.List;

/** A versioned, immutable resolution scenario. */
public class Scheme {
    public Long id;
    public String name;
    public int version;
    public Long parentId;
    public LinkPolicy policy = new LinkPolicy();
    public List<String> loadOrder = new ArrayList<>();
    public String reportJson;
    public String inputHashes;
    public String createdAt;
}
