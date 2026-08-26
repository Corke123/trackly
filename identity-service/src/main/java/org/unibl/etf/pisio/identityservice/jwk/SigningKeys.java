package org.unibl.etf.pisio.identityservice.jwk;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;

public interface SigningKeys {

    List<RSAKey> load();
}
