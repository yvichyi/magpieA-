package ai.magpie.android;
import java.net.URI;
import java.util.Locale;

/** Only a workspace identifier, never a credential or provider configuration. */
final class ModelFavorites {
    static String scope(boolean remote,String origin){
        if(!remote)return "local";
        URI u=URI.create(CoreConfig.remote(origin));
        String scheme=u.getScheme().toLowerCase(Locale.ROOT),host=u.getHost().toLowerCase(Locale.ROOT);
        int port=u.getPort();if((scheme.equals("http")&&port==80)||(scheme.equals("https")&&port==443))port=-1;
        return "remote:"+scheme+"://"+host+(port<0?"":":"+port);
    }
}
