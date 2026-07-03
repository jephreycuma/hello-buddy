package za.co.digital.hellobuddy.stripe;

import com.stripe.Stripe;

public class AppInitializer {
	public static void init() {
        // Set your secret key (keep this safe, use environment variables!)
        Stripe.apiKey = System.getenv("sk_test_51TnMRvHHzApRIw8Sk91v79BFXzqrCrBfEehNW9WjraAlaPHBKvtDCfgltxbhhjtAgkrfQKhurTWRx2WmodTq2pNj00YGSRyIYM"); 
    }

}
