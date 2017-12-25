/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.SimpleRole;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.util.*;

public class TransactionContract extends Contract {

    public TransactionContract(byte[] sealed) throws IOException {
        super(sealed);
    }

    public TransactionContract(byte[] sealed, TransactionPack pack) throws IOException {
        super(sealed, pack);
    }

    public TransactionContract() {
        super();
        Definition cd = getDefinition();
        // by default, transactions expire in 30 days
        cd.setExpiresAt(getCreatedAt().plusDays(30));
    }

    /**
     * The only call needed to setup roles and rights and signatures for {@link TransactionContract}.
     * <p>
     * Transaction contract is immutable, and is issued, onwed and created by the same role, so we create it all in one
     * place, with at least one privateKey. Do not change any roles directly.
     *
     * @param issuers
     */
    public void setIssuer(PrivateKey... issuers) {
        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : issuers) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
            addSignerKey(k);
        }
        registerRole(issuerRole);
        createRole("owner", issuerRole);
        createRole("creator", issuerRole);
    }

    public void setIssuer(PublicKey... swapperKeys) {
        ListRole issuerRole = new ListRole("issuer");
        for (int i = 0; i < swapperKeys.length; i++) {
            SimpleRole swapperRole = new SimpleRole("swapper" + (i+1));

            swapperRole.addKeyRecord(new KeyRecord(swapperKeys[i]));

            registerRole(swapperRole);

            issuerRole.addRole(swapperRole);
        }
//        issuerRole.setMode(ListRole.Mode.ALL);

        registerRole(issuerRole);
        createRole("owner", issuerRole);
        createRole("creator", issuerRole);
    }

    public void addContractToRemove(Contract c) {
        if( !getRevokingItems().contains(c)) {
            Binder data = getDefinition().getData();
            List<Binder> actions = data.getOrCreateList("actions");
            getRevokingItems().add(c);
            actions.add(Binder.fromKeysValues("action", "remove", "id", c.getId()));
        }
    }

    public void addForSwap(Contract contract, PrivateKey fromKey, PublicKey toKey) {

        Contract newContract = contract.createRevision(fromKey);
        newContract.setOwnerKeys(toKey);
        addContractToRemove(contract);
        addNewItems(newContract);
        newContract.seal();
    }

    /**
     * First step of swap procedure. Calls from swapper1 part.
     *
     * Swapper1 create new revisions of existing contracts, change owners,
     * added transactional sections with references to each other and ask two signs
     * and sign contract that was own.
     *
     * @param contract1 - own (swapper1) existing contract
     * @param contract2 - swapper2 existing contract
     * @param fromKey - own (swapper1) private key
     * @param toKey - swapper2 public key
     * @return list of new revisions that need to send to swapper2
     */
    public static List<Contract> startSwap(Contract contract1, Contract contract2, PrivateKey fromKey, PublicKey toKey) {

        List<Contract> swappingContracts = new ArrayList<>();

        Contract newContract1 = contract1.createRevision(fromKey);
        Transactional transactional1 = newContract1.createTransactionalSection();
        transactional1.setId(HashId.createRandom().toBase64String());

        Contract newContract2 = contract2.createRevision();
        Transactional transactional2 = newContract2.createTransactionalSection();
        transactional2.setId(HashId.createRandom().toBase64String());

        Reference reference1 = new Reference();
        reference1.transactional_id = transactional2.getId();
        reference1.type = Reference.TYPE_TRANSACTIONAL;
        reference1.required = true;
        reference1.signed_by = new ArrayList<>();
        reference1.signed_by.add(new SimpleRole("owner", new KeyRecord(fromKey.getPublicKey())));
        reference1.signed_by.add(new SimpleRole("creator", new KeyRecord(toKey)));
        transactional1.addReference(reference1);

        Reference reference2 = new Reference();
        reference2.transactional_id = transactional1.getId();
        reference2.type = Reference.TYPE_TRANSACTIONAL;
        reference2.required = true;
        reference2.signed_by = new ArrayList<>();
        reference2.signed_by.add(new SimpleRole("owner", new KeyRecord(toKey)));
        reference2.signed_by.add(new SimpleRole("creator", new KeyRecord(fromKey.getPublicKey())));
        transactional2.addReference(reference2);



        newContract1.setOwnerKeys(toKey);
        newContract1.seal();
        swappingContracts.add(newContract1);

        newContract2.setOwnerKeys(fromKey.getPublicKey());
        newContract2.seal();
        swappingContracts.add(newContract2);

        return swappingContracts;
    }

    /**
     * Second step of swap procedure. Calls from swapper2 part.
     *
     * Swapper2 got contract from swapper1, sign new contract where he is new owner,
     * add to reference of new contract, that was own, contract_id and point it to contract that will be own.
     * Then sign second contract too.
     *
     * @param swappingContracts - contracts got from swapper1
     * @param key - own (swapper2) private key
     * @return
     */
    public static List<Contract> signPresentedSwap(List<Contract> swappingContracts, PrivateKey key) {

        Set<PublicKey> keys = new HashSet<>();
        keys.add(key.getPublicKey());

        HashId contractHashId = null;
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(keys);

            if(willBeMine) {
                c.addSignatureToSeal(key);
                contractHashId = c.getId();
            }
        }

        for (Contract c : swappingContracts) {
            boolean willBeNotMine = (!c.getOwner().isAllowedForKeys(keys));

            if(willBeNotMine) {

                Set<KeyRecord> krs = new HashSet<>();
                krs.add(new KeyRecord(key.getPublicKey()));
                c.setCreator(krs);

                if(c.getTransactional() != null && c.getTransactional().getReferences() != null) {
                    for (Reference rm : c.getTransactional().getReferences()) {
                        rm.contract_id = contractHashId;
                    }
                } else {
                    return swappingContracts;
                }

                c.seal();
                c.addSignatureToSeal(key);
            }
        }

        return swappingContracts;
    }

    /**
     * Third and final step of swap procedure. Calls from swapper1 part.
     *
     * Swapper1 got contracts from swapper2 and finally sign contract that will be own.
     *
     * @param swappingContracts
     * @param key
     * @return
     */
    public static List<Contract> finishSwap(List<Contract> swappingContracts, PrivateKey key) {

        Set<PublicKey> keys = new HashSet<>();
        keys.add(key.getPublicKey());
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(keys);

            if(willBeMine) {
                c.addSignatureToSeal(key);
            }
        }

        return swappingContracts;
    }

    // for test purposes only



    public static List<Contract> startSwap__WrongSignTest(Contract contract1, Contract contract2, PrivateKey fromKey, PublicKey toKey, PrivateKey wrongKey) {

        List<Contract> swappingContracts = new ArrayList<>();

        Transactional transactional1 = contract1.createTransactionalSection();
        transactional1.setId("" + Do.randomInt(1000000000));

        Transactional transactional2 = contract1.createTransactionalSection();
        transactional2.setId("" + Do.randomInt(1000000000));

        Reference reference1 = new Reference();
        reference1.transactional_id = transactional2.getId();
        reference1.type = Reference.TYPE_TRANSACTIONAL;
        reference1.required = true;
        reference1.signed_by = new ArrayList<>();
        reference1.signed_by.add(new SimpleRole("owner", new KeyRecord(fromKey.getPublicKey())));
        reference1.signed_by.add(new SimpleRole("creator", new KeyRecord(toKey)));
        transactional1.addReference(reference1);

        Reference reference2 = new Reference();
        reference2.transactional_id = transactional1.getId();
        reference2.type = Reference.TYPE_TRANSACTIONAL;
        reference2.required = true;
        reference2.signed_by = new ArrayList<>();
        reference2.signed_by.add(new SimpleRole("owner", new KeyRecord(toKey)));
        reference2.signed_by.add(new SimpleRole("creator", new KeyRecord(fromKey.getPublicKey())));
        transactional2.addReference(reference2);



        Contract newContract1 = contract1.createRevision(transactional1, wrongKey);
        newContract1.setOwnerKeys(toKey);
        newContract1.seal();
        swappingContracts.add(newContract1);

        Contract newContract2 = contract2.createRevision(transactional2);
        newContract2.setOwnerKeys(fromKey.getPublicKey());
        newContract2.seal();
        swappingContracts.add(newContract2);

        return swappingContracts;
    }

    public static List<Contract> signPresentedSwap__WrongSignTest(List<Contract> swappingContracts, PrivateKey key, PrivateKey wrongKey) {

        HashId contractHashId = null;
        for (Contract c : swappingContracts) {
            for (PublicKey k : c.getOwner().getKeys()) {
                if(k.equals(key.getPublicKey())) {

                    c.addSignatureToSeal(wrongKey);
                    contractHashId = c.getId();
                }
            }
        }

        for (Contract c : swappingContracts) {
            boolean isMyContract = false;
            for (PublicKey k : c.getOwner().getKeys()) {
                if(!k.equals(key.getPublicKey())) {
                    isMyContract = true;
                    break;
                }
            }
            if(isMyContract) {

                Set<KeyRecord> krs = new HashSet<>();
                krs.add(new KeyRecord(key.getPublicKey()));
                c.setCreator(krs);

                if(c.getTransactional() != null) {
                    for (Reference rm : c.getTransactional().getReferences()) {
                        rm.contract_id = contractHashId;
                    }
                } else {
                    return swappingContracts;
                }

                c.seal();
                c.addSignatureToSeal(wrongKey);
            }
        }

        return swappingContracts;
    }

    public static List<Contract> finishSwap__WrongSignTest(List<Contract> swappingContracts, PrivateKey key, PrivateKey wrongKey) {

        for (Contract c : swappingContracts) {
            boolean isMyContract = false;
            for (PublicKey k : c.getOwner().getKeys()) {
                if(!k.equals(key.getPublicKey())) {
                    isMyContract = true;
                    break;
                }
            }
            if(!isMyContract) {
                c.addSignatureToSeal(wrongKey);
            }
        }

        return swappingContracts;
    }
}
