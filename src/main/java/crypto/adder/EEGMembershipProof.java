package crypto.adder;

import crypto.ExponentialElGamalCiphertext;
import crypto.IProof;
import crypto.IPublicKey;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 *  Zero-knowledge proof of set membership.
 *
 * Suppose we have a ciphertext \f$\langle G, H \rangle = \langle
 * g^r, h^r f^m \rangle\f$ and we wish to prove that \f$m \in
 * \{i_1, \ldots, i_n\}\f$. Furthermore, suppose that \f$m =
 * i_x\f$. We can use the following OR-composition proof of
 * knowledge.
 *
 * <table border=0>
 * <tr><th>Prover</th><th></th><th>Verifier</th>
 * <tr><td>\f$c_1, \ldots, c_n \stackrel{\texttt{r}}{\leftarrow}
 * \mathrm{Z}_q\f$</td><td></td><td></td></tr>
 *
 * <tr><td>\f$s_1, \ldots, s_n \stackrel{\texttt{r}}{\leftarrow}
 * \mathrm{Z}_q\f$</td><td></td><td></td></tr>
 *
 * <tr><td>if \f$i \neq x\f$, then \f$y_i \leftarrow g^{s_i}G^{-c_i}\f$ and \f$z_i \leftarrow h^{s_i}(H / f^{i_i})^{-c_i}\f$<br> otherwise, \f$y_i = g^t\f$ and \f$z_i
 *  = h^t\f$</td><td></td><td></td></tr>
 *
 * <tr><td></td><td>\f$\stackrel{y_1, \ldots, y_n, z_1, \ldots,
 * z_n}{\longrightarrow}\f$</td><td></td></tr>
 *
 * <tr><td></td><td></td><td>\f$c \stackrel{\texttt{r}}{\leftarrow}
 * \mathrm{Z}_q\f$</td></tr>
 *
 * <tr><td></td><td>\f$\stackrel{c}{\longleftarrow}\f$</td><td></td></tr>
 *
 * <tr><td>\f$c_x = c - c_1 - \cdots -
 * c_n\f$</td><td></td><td></td></tr>
 *
 * <tr><td>\f$s_x = t + c_x r\f$</td><td></td><td></td></tr>
 *
 * <tr><td></td><td>\f$\stackrel{s_1, \ldots, s_n, c_1, \ldots,
 * c_n}{\longrightarrow}\f$</td><td></td></tr>
 *
 * <tr><td></td><td></td>
 * <td>
 * \f$g^{s_i} \stackrel{?}{=} y_i G^{c_i}\f$ <br>
 * \f$h^{s_i} \stackrel{?}{=} z_i (H/f^{i_i})^{c_i}, i \in
 * \{i_1, \ldots, i_n\}\f$<br>
 * \f$c \stackrel{?}{=} c_1 + \cdots + c_n\f$ </td></tr>
 *
 * </table>
 *
 * Now, we can make this proof non-interactive by employing the
 * Fiat-Shamir heuristic.  Then, the prover will send the tuple
 * \f$\langle y_1, z_1, \ldots, y_n, z_n, c, s_1, \ldots, s_n,
 * c_1, \ldots, c_n\rangle\f$, where \f$c = \mathcal{H}(g, h, G,
 * H, y_1, z_1, \ldots, y_n, z_n)\f$ and \f$\mathcal{H}\f$ is a
 * cryptographic hash function. Verification is performed by
 * testing that \f$c_1 + \cdots + c_n = \mathcal{H}(g, h, G, H,
 * g^{s_1}G^{-c_1}, h^{s_1}(H/f^{i_1})^{-c_1}, \ldots,
 * g^{s_n}G^{-c_n}, h^{s_n}(H/f^{i_n})^{-c_n})\f$.
 *
 *  @author David Walluck
 *  @version $LastChangedRevision$ $LastChangedDate$
 *  @since 0.0.1
 */
public class EEGMembershipProof implements IProof<ExponentialElGamalCiphertext> {

    private AdderInteger p;
    private AdderInteger q;
    private List<AdderInteger> yList;
    private List<AdderInteger> zList;
    private List<AdderInteger> sList;
    private List<AdderInteger> cList;

    /**
     * Constructs a new <code>MembershipProof</code> object with the specified prime.
     */
    public EEGMembershipProof(AdderInteger bigG, AdderInteger bigH, AdderInteger r, AdderPublicKey pubKey, AdderInteger value, List<AdderInteger> domain) {

        yList = new ArrayList<>();
        zList = new ArrayList<>();
        sList = new ArrayList<>();
        cList = new ArrayList<>();

        compute(bigG, bigH, r, pubKey, value, domain);
    }

    /**
     * Constructs a new <code>MembershipProof</code> object with the specified
     * prime.
     *
     * @param p     the prime
     * @param q     the order of the group generated by g, a primitive root mod p
     * @param yList the temporary list of group members, including one valid member used to encrypt
     * @param zList the temporary list of fake ciphertexts, including one valid one
     * @param sList List of values of the form cr + t used to compute the ZK proof
     * @param cList List of commitments, including several random numbers and by one computation on the hash of the commitment string
     */
    private EEGMembershipProof(AdderInteger p, AdderInteger q, List<AdderInteger> yList, List<AdderInteger> zList,
                               List<AdderInteger> sList, List<AdderInteger> cList) {

        this.p = p;
        this.q = q;
        this.yList = yList;
        this.zList = zList;
        this.sList = sList;
        this.cList = cList;
    }

    /**
     * Computes and constructs a proof of the operation of two ciphertexts (itself a ciphertext) by using
     * values from the individual ciphertexts and their respective proofs.
     *
     * @param ctext1    the first ciphertext to be used to construct the new combined proof
     * @param r1        the r value of the first ciphertext
     * @param domain1   the domain of the proof of the first ciphertext
     * @param ctext2    the second ciphertext to be used to construct the new combined proof
     * @param r2        the r values of the second ciphertext
     * @param domain2   the domain of the proof of the second ciphertext
     * @param newDomain the domain of the new proof (the sum of domain1 and domain2, usually)
     * @param pubKey    the public key used to encrypt the message.
     */
    public EEGMembershipProof(  ExponentialElGamalCiphertext ctext1, AdderInteger r1, List<AdderInteger> domain1,
                                ExponentialElGamalCiphertext ctext2, AdderInteger r2, List<AdderInteger> domain2,
                                List<AdderInteger> newDomain,        AdderPublicKey pubKey) {

        yList = new ArrayList<>();
        zList = new ArrayList<>();
        sList = new ArrayList<>();
        cList = new ArrayList<>();


        ExponentialElGamalCiphertext c1 = new ExponentialElGamalCiphertext( ctext1.getG(), ctext1.getH(),
                                                                            r1, pubKey.getP(),
                                                                            new EEGMembershipProof(ctext1.getProof()), ctext1.getSize());

        ExponentialElGamalCiphertext c2 = new ExponentialElGamalCiphertext( ctext2.getG(), ctext2.getH(),
                                                                            r2, pubKey.getP(),
                                                                            new EEGMembershipProof(ctext2.getProof()), ctext2.getSize());

        computeOperationProof(c1, r1, domain1, c2, r2, domain2, newDomain, pubKey);

    }

    public EEGMembershipProof(EEGMembershipProof proof) {
        yList = new ArrayList<>();
        yList.addAll(proof.yList);

        zList = new ArrayList<>();
        zList.addAll(proof.zList);

        sList = new ArrayList<>();
        sList.addAll(proof.sList);

        cList = new ArrayList<>();
        cList.addAll(proof.cList);

        p = proof.p;
        q = proof.q;
    }

    private void computeOperationProof(ExponentialElGamalCiphertext ctext1, AdderInteger r1, List<AdderInteger> domain1,
                                       ExponentialElGamalCiphertext ctext2, AdderInteger r2, List<AdderInteger> domain2,
                                       List<AdderInteger> newDomain,        AdderPublicKey pubKey) {

        /* Get p and q from the key */
        this.p = pubKey.getP();
        this.q = pubKey.getQ();

        /* Get g, h, and f */
        AdderInteger g = new AdderInteger(pubKey.getG(), this.p);
        AdderInteger h = pubKey.getH();
        AdderInteger f = pubKey.getF();

        /* bigG (g^r), bigH (g^(rx) * f^m), and r */
        AdderInteger bigG = ctext1.getG().multiply(ctext2.getG());
        AdderInteger bigH = ctext1.getH().multiply(ctext2.getH());

        int indexInDomain =0;

        /* Used in commitment process */
        AdderInteger t = AdderInteger.random(q);

        StringBuilder sb = new StringBuilder(4096);

        /* Append all the numbers to the string*/
        sb.append(g);
        sb.append(h);
        sb.append(bigG);
        sb.append(bigH);

        /* Reshape the proof lists to fit the new domain */
        reviseProofs(ctext1.getProof(), domain1, ctext2.getProof(), domain2, newDomain, q);

        /* Iterate over the domain */
        for (int i = 0; i < newDomain.size(); i++) {

            AdderInteger y;
            AdderInteger z;
            AdderInteger d = newDomain.get(i);

            AdderInteger s1 = ctext1.getProof().sList.get(i);
            AdderInteger s2 = ctext2.getProof().sList.get(i);

            AdderInteger c1 = ctext1.getProof().cList.get(i);
            AdderInteger c2 = ctext1.getProof().cList.get(i);

            /* s' = s1 + s2 */
            sList.add(s1.add(s2));

            /* c' = c1 + c2 */
            cList.add(c1.add(c2));

            /* This will be needed for computing z_i */
            AdderInteger negC1 = c1.negate();
            AdderInteger negC2 = c2.negate();

            /* This is essentially the message corresponding to domain member d mapped into G */
            AdderInteger fpow = f.pow(d);

            /* Compute a group member y = g^s * (g^r)^(-c) = g^(s - r*c) */
            AdderInteger y1 = g.pow(s1).multiply(ctext1.getG().pow(negC1));
            AdderInteger y2 = g.pow(s2).multiply(ctext2.getG().pow(negC2));

            /* Now this is y1*y2 / [g^(r2*c1+r1*c2)] = g^(s'-r'c') = y(s',r',c') = y' */
            y = y1.multiply(y2).divide(g.pow(r2.multiply(c1).add(r1.multiply(c2))));

            /* Compute a cipher, of the form z = g^xs * [(g^rx * f^m)/f^d]^(-c_i) = g^[x(s - rc_i)] * f^[c_i*(d - m)] */
            AdderInteger z1 = h.pow(s1).multiply(ctext1.getH().divide(fpow).pow(negC1));
            AdderInteger z2 = h.pow(s2).multiply(ctext2.getH().divide(fpow).pow(negC2));

            /* Now this is z1*z2 / [f^(m2*c1+m1*c2)] = z1*z2 / [ bigH2^c1 * bigH1^c2 ] = z(y', s',c') = z' */
            z = z1.multiply(z2).divide(ctext2.getH().pow(c1).multiply(ctext1.getH().pow(c2)));

            /* If this is true, then this means that d=m */
            if (bigH.divide(fpow).equals(h.pow(r1.add(r2)))) {

                y = g.pow(t);
                z = h.pow(t);
                cList.set(i,AdderInteger.ZERO);
                sList.set(i,AdderInteger.ZERO);
                indexInDomain = i;
            }

            /* Add our random ciphers and members to their respective lists */
            yList.add(y);
            zList.add(z);

            sb.append(y);
            sb.append(z);
        }

        AdderInteger c = new AdderInteger(Util.sha1(sb.toString()), q, 16).mod(q);
        AdderInteger realC =  new AdderInteger(c,q);

        for (AdderInteger fakeC : cList) realC = realC.subtract(fakeC);

        /* Note that realC is now c - (sum(cList)) = hash(sb) - sum(cList). If we tack this onto existing cList, then
         * sum(cList) = hash(sb). When this gets verified, then cChoices = sum(cList) = hash(sb) = c
         */

        /* This will ensure that y = g^(s' - r'c') = g^(realC*r'+t - r'*realC) = g^t which is what was committed */
        /* Since z = y^x * f^[c(d-m)] = (g^t)^x f^[realC(d-m)] = h^t when d=m which is what was committed */
        cList.set(indexInDomain, realC);
        sList.set(indexInDomain, realC.multiply(r1.add(r2)).add(t));
    }

    private void reviseProofs(EEGMembershipProof proof1,    List<AdderInteger> domain1,
                              EEGMembershipProof proof2,    List<AdderInteger> domain2,
                              List<AdderInteger> newDomain, AdderInteger q){

        /* Want to shift around the sList and cList for the proofs to achieve the new domain */
        List<AdderInteger> newCList1 = new ArrayList<>();
        List<AdderInteger> newSList1 = new ArrayList<>();
        AdderInteger min1 = domain1.get(0);
        AdderInteger max1 = domain1.get(domain1.size()-1);

        List<AdderInteger> newCList2 = new ArrayList<>();
        List<AdderInteger> newSList2 = new ArrayList<>();
        AdderInteger min2 = domain2.get(0);
        AdderInteger max2 = domain2.get(domain2.size()-1);

        int j=0;
        int k=0;

        for (int i=newDomain.get(0).intValue(); i<=newDomain.get(newDomain.size()-1).intValue(); i++) {

            if (i < min1.intValue() || i > max1.intValue()) {
                newCList1.add(AdderInteger.random(q));
                newSList1.add(AdderInteger.random(q));
            }
            else {
                newCList1.add(proof1.cList.get(j));
                newSList1.add(proof1.sList.get(j));
                j++;
            }

            if (i < min2.intValue() || i > max2.intValue()) {
                newCList2.add(AdderInteger.random(q));
                newSList2.add(AdderInteger.random(q));
            }
            else {
                newCList2.add(proof2.cList.get(k));
                newSList2.add(proof2.sList.get(k));
                k++;
            }
        }

        /* Replace the old cLists and sLists*/
        proof1.cList = newCList1;
        proof1.sList = newSList1;

        proof2.cList = newCList2;
        proof2.sList = newSList2;

    }


    /**
     * Computes the proof as detailed above by forming one proof for each
     * element of the domain. All but one of the proofs (the one corresponding
     * to \em value) will be fake.
     *
     * @param bigG
     * @param bigH
     * @param r
     * @param pubKey the public key used to encrypt the message.
     * @param value  the plaintext value of the ciphertext.
     * @param domain the domain of possible values of the plaintext.
     */
    private void compute(AdderInteger bigG, AdderInteger bigH, AdderInteger r, AdderPublicKey pubKey, AdderInteger value, List<AdderInteger> domain) {

        /* Get p and q from the key */
        this.p = pubKey.getP();
        this.q = pubKey.getQ();

        /* Get g, h, and f */
        AdderInteger g = new AdderInteger(pubKey.getG(), this.p);
        AdderInteger h = pubKey.getH();
        AdderInteger f = pubKey.getF();

        /* bigG (g^r), bigH (g^(rx) * f^m), and r */

        /* Generate a random value t */
        AdderInteger t = AdderInteger.random(q);

        /*
         * Create a StringBuffer for holding information to create a commitment string
         *
         *  Note that this string will be of the form:
         *       (g, g^x, g^r, (g^(rx) * f^m), y_0, z_0, ..., y_n, z_n)
         */
        StringBuilder sb = new StringBuilder(4096);

        /* Append all the numbers to the string*/
        sb.append(g);
        sb.append(h);
        sb.append(bigG);
        sb.append(bigH);

        /* Initialize our domain counter */
        int indexInDomain = 0;

        /* Iterate over the domain */
        for (int i = 0; i < domain.size(); i++) {

            AdderInteger y;
            AdderInteger z;
            AdderInteger d = domain.get(i);

            /* See if the value is this particular member of the domain */
            if (d.equals(value)) {

                /* If it is, fill c_i and s_i with dummy values for now */
                sList.add(AdderInteger.ZERO);
                cList.add(AdderInteger.ZERO);

                /* Compute random group member */
                y = g.pow(t);

                /* compute a random cipher, as part of the commitment process */
                z = h.pow(t);

                /* Record the index of the valid value */
                indexInDomain = i;
            } else {

                /* If we don't have a valid value, generate random numbers for c_i and s_i */
                sList.add(AdderInteger.random(q));
                cList.add(AdderInteger.random(q));
                AdderInteger s = sList.get(i);
                AdderInteger c = cList.get(i);

                /* This will be needed for computing z_i */
                AdderInteger negC = c.negate();

			    /* This is essentially the message corresponding to domain member d mapped into G */
                AdderInteger fpow = f.pow(d);

                /* Compute a group member g^s * (g^r)^(-c_i) = g^(s - r*c_i) */
                y = g.pow(s).multiply(bigG.pow(negC));

                /* Compute a cipher, of the form g^xs * [(g^rx * f^m)/f^d]^(-c_i) = g^[x(s - rc_i)] * f^[c_i*(d - m)] */
                z = h.pow(s).multiply(bigH.divide(fpow).pow(negC));
            }

            /* Add our random ciphers and members to their respective lists */
            yList.add(y);
            zList.add(z);

            /* Add them to the commitment string */
            sb.append(y);
            sb.append(z);
        }

        /* Hash the commitment string */
        String s = sb.toString();
        String cHash = Util.sha1(s);

        /* From the hash, construct a numerical value */
        AdderInteger c1 = new AdderInteger(cHash, q, 16).mod(q);
        AdderInteger realC = new AdderInteger(c1, q);

        /* Now subtract all of the generated fake commits off the hash value (note, the valid value will still be 0 here) */
        for (AdderInteger fakeC : cList)
            realC = realC.subtract(fakeC);

        /* Note that realC (call it p) is now c1 - (sum(cList)) */

        /* Compute pr + t using our real commitment value and add it in the right place */
        sList.set(indexInDomain, realC.multiply(r).add(t));

        /* Add our real commitment value into the commit list in the right place */
        cList.set(indexInDomain, realC);
    }

    /**
     * Verifies the proof given the ciphertext, public key, and
     * domain.
     *
     * @param ciphertext the ciphertext
     * @param domain     the domain
     * @return true if the proof is valid
     */
    public boolean verify(ExponentialElGamalCiphertext ciphertext, IPublicKey PEK, List<Integer> domain) {

        AdderPublicKey pubKey = (AdderPublicKey) PEK;

        /* Extract necessary key components for computation */
        p = pubKey.getP();
        q = pubKey.getQ();
        AdderInteger g = pubKey.getG();
        AdderInteger h = pubKey.getH();
        AdderInteger f = pubKey.getF();

        /* Get the cipher's randomness and encrypted value*/
        /* bigG (g^r), bigH (g^(rx) * f^m) */
        AdderInteger bigG = ciphertext.getG();
        AdderInteger bigH = ciphertext.getH();

        /* This will be our commit value that we reconstruct */
        AdderInteger cChoices = new AdderInteger(AdderInteger.ZERO, q);

        /* Build a new commit string so we can reconstruct our commit value */
        StringBuilder sb = new StringBuilder(4096);

        /* start the string off the right way */
        sb.append(g);
        sb.append(h);
        sb.append(bigG);
        sb.append(bigH);

        try {

            /* Iterate over all the commits, fake and otherwise */
            for (int i = 0; i < cList.size(); i++) {

                /* Get out the domain value (i.e. the possible message m) */
                AdderInteger d = new AdderInteger(domain.get(i));

                /* Map the value into the group via f */
                AdderInteger fpow = f.pow(d);


                /* extract the commit value and cr + t (or the random values) */
                AdderInteger s = sList.get(i);
                AdderInteger c = cList.get(i);

                /* Compute -c_i so it will fall out of z_i for fake commitments */
                AdderInteger negC = c.negate();

                /*
                 * add this commit value to reconstruct our hashed value
                 * cChoices = sum(c_i), where one c_i is realC from compute, giving us
                 * cChoices = c_0 + ... + realC + ... c_n = c_0 + ... (c1 - (c_0 + ... + 0 + ... + c_n) + ... c_n
                 * cChoices = c_0 - c_0 + ... c - 0 + ... c_n - c_n
                 * cChoices = c1 eventually
                 */
                cChoices = cChoices.add(c);

                /* Compute the y-values used in the commit string */
                sb.append(g.pow(s).multiply(bigG.pow(negC)));

                /* Compute the z-values used in the commit string */
                sb.append(h.pow(s).multiply(bigH.divide(fpow).pow(negC)));
            }

            /* Now take the hash of the commit string and convert it to a number */
            String cHash = Util.sha1(sb.toString());
            AdderInteger newC = new AdderInteger(cHash, q, 16).mod(q);

            /* Ensure that cChoices (i.e. the real commit) matches the hashed value of the commit string */
            return (cChoices.equals(newC));

        }
        catch (IndexOutOfBoundsException e) {
            /* This happens if the domain used in verification is smaller than the
             * one used for computation of the proof -- automatic failure for verification
             */
            return false;
        }
    }

    /**
     * Creates a <tt>MembershipProof</tt> from the string standard representation
     * as described in the {@link #toString} method.
     *
     * @param s a string that specifies a <tt>MembershipProof</tt>
     * @return a        <tt>MembershipProof</tt> with the specified values
     */
    public static EEGMembershipProof fromString(String s) {
        StringTokenizer st = new StringTokenizer(s, "pyzsc", true);
        int numTokens = st.countTokens() - 2;

        if ((numTokens % 8) != 0) {
            throw new
                    InvalidMembershipProofException("number of tokens not divisible by 8");
        }

        int count = numTokens / 8;

        try {
            if (!st.nextToken().equals("p")) {
                throw new InvalidMembershipProofException("expected token: `p\'");
            }

            AdderInteger p = new AdderInteger(st.nextToken());

            AdderInteger q
                    = p.subtract(AdderInteger.ONE).divide(AdderInteger.TWO);

            List<AdderInteger> yList
                    = new ArrayList<>(count);

            for (int ySize = 0; ySize < count; ySize++) {
                if (!st.nextToken().equals("y")) {
                    throw new
                            InvalidMembershipProofException("expected token: `y\'");
                }

                yList.add(new AdderInteger(st.nextToken(), p));
            }

            List<AdderInteger> zList
                    = new ArrayList<>(count);

            for (int zSize = 0; zSize < count; zSize++) {
                if (!st.nextToken().equals("z")) {
                    throw new
                            InvalidMembershipProofException("expected token: `z\'");
                }

                zList.add(new AdderInteger(st.nextToken(), p));
            }

            List<AdderInteger> sList
                    = new ArrayList<>(count);

            for (int sSize = 0; sSize < count; sSize++) {
                if (!st.nextToken().equals("s")) {
                    throw new
                            InvalidMembershipProofException("expected token: `s\'");
                }

                sList.add(new AdderInteger(st.nextToken(), q));
            }

            List<AdderInteger> cList
                    = new ArrayList<>(count);

            for (int cSize = 0; cSize < count; cSize++) {
                if (!st.nextToken().equals("c")) {
                    throw new
                            InvalidMembershipProofException("expected token: `c\'");
                }

                cList.add(new AdderInteger(st.nextToken(), q));
            }

            return new EEGMembershipProof(p, q, yList, zList, sList, cList);
        } catch (NoSuchElementException | NumberFormatException nsee) {
            throw new InvalidMembershipProofException(nsee.getMessage());
        }
    }

    /**
     * Returns a <code>String</code> object representing this <code>MembershipProof</code>.
     *
     * @return the string representation of this proof
     */
    public String toString() {

        StringBuilder sb = new StringBuilder(4096);

        sb.append("p");
        sb.append(p);

        for (AdderInteger y : yList) {
            sb.append("y");
            sb.append(y);
        }

        for (AdderInteger z : zList) {
            sb.append("z");
            sb.append(z);
        }

        for (AdderInteger s : sList) {
            sb.append("s");
            sb.append(s);
        }

        for (AdderInteger c1 : cList) {
            sb.append("c");
            sb.append(c1);
        }

        return sb.toString();
    }

}

